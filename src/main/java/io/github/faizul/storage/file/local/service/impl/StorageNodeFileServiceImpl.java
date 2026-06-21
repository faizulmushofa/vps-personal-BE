package io.github.faizul.storage.file.local.service.impl;

import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.storage.file.repository.FileRepository;
import io.github.faizul.storage.file.local.service.StorageNodeFileService;
import io.github.faizul.storage.file.dtos.FileResponse;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.storage.upload.service.UploadStorageService;
import io.github.faizul.user.repository.UserRepository;
import io.github.faizul.user.model.User;
import io.github.faizul.storage.file.dtos.UserProfileResponse;
import io.github.faizul.storage.file.dtos.UserStorageResponse;
import io.github.faizul.storage.file.dtos.UserStorageSummary;
import io.github.faizul.storage.file.dtos.UpdateQuotaRequest;
import io.github.faizul.storage.share.repository.FileSharedRepository;
import io.github.faizul.storage.file.model.File;
import io.github.faizul.user.repository.ExternalAccountRepository;
import io.github.faizul.storage.file.service.client.GoogleDriveClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.NoSuchElementException;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StorageNodeFileServiceImpl implements StorageNodeFileService {

        private final FileRepository fileRepository;
        private final CurrentUserContext currentUserContext;
        private final UploadStorageService uploadStorageClient;
        private final UserRepository userRepository;
        private final FileSharedRepository fileSharedRepository;
        private final UserActivityService userActivityService;
        private final ExternalAccountRepository externalAccountRepository;
        private final GoogleDriveClient googleDriveClient;

        @Override
        public Mono<FileResponse> findByUUID(UUID uuid) {
                return currentUserContext.getUserId()
                                .flatMap(userId -> fileRepository.findById(uuid)
                                                .switchIfEmpty(
                                                                Mono.error(new NoSuchElementException(
                                                                                "Berkas tidak ditemukan!")))
                                                .flatMap(file -> {
                                                        if (file.getUserId().equals(userId)) {
                                                                return Mono.just(file);
                                                        }
                                                        return fileSharedRepository.findByFileIdAndUserId(file.getId(), userId)
                                                                        .map(shared -> shared.getExpiresAt() == null || LocalDateTime.now(ZoneOffset.UTC).isBefore(shared.getExpiresAt()))
                                                                        .defaultIfEmpty(false)
                                                                        .flatMap(hasAccess -> {
                                                                                if (Boolean.TRUE.equals(hasAccess)) {
                                                                                        return Mono.just(file);
                                                                                }
                                                                                return Mono.error(new AccessDeniedException(
                                                                                                "Anda tidak memiliki akses untuk melihat berkas ini"));
                                                                        });
                                                }))
                                .map(file -> new FileResponse(
                                                file.getId(),
                                                file.getOriginalFileName(),
                                                file.getSize(),
                                                file.getCreatedAt(),
                                                file.getProvider(),
                                                file.getExternalAccountId(),
                                                null));
        }

        @Override
        public Mono<String> deleteByUUID(UUID uuid, org.springframework.web.server.ServerWebExchange exchange) {
                return currentUserContext.getUserId()
                                .flatMap(userId -> fileRepository.findById(uuid)
                                                .switchIfEmpty(
                                                                Mono.error(new NoSuchElementException(
                                                                                "Berkas tidak ditemukan!")))
                                                .flatMap(file -> {
                                                        if (!file.getUserId().equals(userId)) {
                                                                return Mono.error(new AccessDeniedException(
                                                                                "Anda tidak memiliki akses untuk menghapus berkas ini"));
                                                        }
                                                        return Mono.just(file);
                                                }))
                                .flatMap(file -> uploadStorageClient
                                                .deleteFile(file.getUserId(), file.getId().toString())
                                                .onErrorResume(e -> {
                                                        System.err.println("Warning: Gagal menghapus file fisik di storage node: " + e.getMessage());
                                                        return Mono.empty();
                                                })
                                                .then(fileRepository.deleteById(file.getId()))
                                                .flatMap(unused -> userActivityService.log(file.getUserId(), "DELETE_FILE", "Menghapus berkas: " + file.getOriginalFileName(), exchange))
                                                .thenReturn(file.getOriginalFileName()));
        }

        @Override
        public Mono<UUID> resolveFileId(String fileIdString, Long userId) {
                try {
                        return Mono.just(UUID.fromString(fileIdString));
                } catch (IllegalArgumentException e) {
                        return fileRepository.findByStorageNameAndProvider(fileIdString, "GOOGLE_DRIVE")
                                        .map(File::getId)
                                        .switchIfEmpty(Mono.defer(() -> {
                                                log.info("Berkas Google Drive {} tidak ditemukan di database lokal. Memicu JIT import...", fileIdString);
                                                return externalAccountRepository.findByUserIdAndProvider(userId, "GOOGLE")
                                                                .flatMap(account -> googleDriveClient.getFileMetadata(account.getId(), fileIdString)
                                                                                .flatMap(metadata -> {
                                                                                        String name = (String) metadata.get("name");
                                                                                        long size = 0L;
                                                                                        if (metadata.get("size") != null) {
                                                                                                try {
                                                                                                        size = Long.parseLong(metadata.get("size").toString());
                                                                                                } catch (NumberFormatException ignored) {}
                                                                                        }

                                                                                        log.info("Metadata Google Drive berhasil didapatkan untuk berkas: {}. Menyimpan ke database lokal...", name);
                                                                                        File newFile = File.builder()
                                                                                                        .id(UUID.randomUUID())
                                                                                                        .userId(userId)
                                                                                                        .originalFileName(name)
                                                                                                        .storageName(fileIdString)
                                                                                                        .size(size)
                                                                                                        .provider("GOOGLE_DRIVE")
                                                                                                        .externalAccountId(account.getId())
                                                                                                        .build();
                                                                                        return fileRepository.save(newFile)
                                                                                                        .map(File::getId);
                                                                                })
                                                                )
                                                                .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan!")));
                                        }));
                }
        }

        @Override
        public Flux<FileResponse> getAllByUserId() {
                return currentUserContext.getUserId()
                                .flatMapMany(userId -> fileRepository.findByUserId(userId))
                                .map(file -> new FileResponse(
                                                file.getId(),
                                                file.getOriginalFileName(),
                                                file.getSize(),
                                                file.getCreatedAt(),
                                                file.getProvider(),
                                                file.getExternalAccountId(),
                                                null));
        }

        @Override
        public Flux<FileResponse> getAllForAdmin() {
                return fileRepository.findAll()
                                .map(file -> new FileResponse(
                                                file.getId(),
                                                file.getOriginalFileName(),
                                                file.getSize(),
                                                file.getCreatedAt(),
                                                file.getProvider(),
                                                file.getExternalAccountId(),
                                                null));
        }

        @Override
        public Mono<UserProfileResponse> getCurrentUserProfile() {
                return currentUserContext.getUserId()
                                .flatMap(userRepository::findById)
                                .map(user -> new UserProfileResponse(
                                                user.getId(),
                                                user.getUsername(),
                                                user.getEmail()));
        }

        @Override
        public Mono<UserStorageResponse> getCurrentUserStorage() {
                return currentUserContext.getUserId()
                                .flatMap(userId -> userRepository.findById(userId)
                                                .flatMap(user -> {
                                                        Mono<User> activeUserMono = Mono.just(user);
                                                        if (user.getSubscriptionExpiresAt() != null && user.getSubscriptionExpiresAt().isBefore(java.time.LocalDateTime.now())) {
                                                                user.setSubscriptionTier("FREEMIUM");
                                                                user.setStorageQuota(1073741824L);
                                                                user.setSubscriptionExpiresAt(null);
                                                                activeUserMono = userRepository.save(user);
                                                        }
                                                        return activeUserMono;
                                                })
                                                .flatMap(user -> fileRepository.calculateUsedStorageByUserId(userId)
                                                                .map(usedBytes -> new UserStorageResponse(
                                                                                usedBytes,
                                                                                user.getStorageQuota() != null
                                                                                                ? user.getStorageQuota()
                                                                                                : 1073741824L,
                                                                                false,
                                                                                0L,
                                                                                0L))));
        }

        @Override
        public Flux<UserStorageSummary> getUserStorageSummary() {
                return userRepository.findAll()
                                .flatMap(user -> fileRepository.calculateUsedStorageByUserId(user.getId())
                                                .map(usedBytes -> new UserStorageSummary(
                                                                user.getId(),
                                                                user.getUsername(),
                                                                user.getEmail(),
                                                                usedBytes,
                                                                user.getStorageQuota() != null ? user.getStorageQuota()
                                                                                : 1073741824L)));
        }

        @Override
        public Mono<UserStorageSummary> updateUserQuota(Long id, UpdateQuotaRequest request, org.springframework.web.server.ServerWebExchange exchange) {
                return currentUserContext.getUserId()
                                .flatMap(adminId -> userRepository.findById(id)
                                                .switchIfEmpty(Mono.error(new NoSuchElementException("User Not Found")))
                                                .flatMap(user -> {
                                                        user.setStorageQuota(request.quotaBytes());
                                                        return userRepository.save(user);
                                                })
                                                .flatMap(user -> fileRepository.calculateUsedStorageByUserId(user.getId())
                                                                .map(usedBytes -> new UserStorageSummary(
                                                                                user.getId(),
                                                                                user.getUsername(),
                                                                                user.getEmail(),
                                                                                usedBytes,
                                                                                user.getStorageQuota()))
                                                                .flatMap(summary -> userActivityService.log(adminId, "UPDATE_USER_QUOTA", 
                                                                                "Mengubah kuota penyimpanan user " + summary.username() + " (ID: " + id + ") menjadi " + request.quotaBytes() + " bytes", exchange)
                                                                                .thenReturn(summary))
                                                ));
        }
}
