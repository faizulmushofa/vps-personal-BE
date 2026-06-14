package io.github.faizul.File.core.NodeStorage;

import io.github.faizul.File.core.FileRepository;
import io.github.faizul.File.core.FileService;
import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.Storage.upload.UploadStorageService;
import io.github.faizul.User.core.UserRepository;
import io.github.faizul.User.core.User;
import io.github.faizul.File.dtos.UserProfileResponse;
import io.github.faizul.File.dtos.UserStorageResponse;
import io.github.faizul.File.dtos.UserStorageSummary;
import io.github.faizul.File.dtos.UpdateQuotaRequest;
import io.github.faizul.File.share.FileSharedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.NoSuchElementException;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class FileServiceImp implements FileService {

        private final FileRepository fileRepository;
        private final CurrentUserContext currentUserContext;
        private final UploadStorageService uploadStorageClient;
        private final UserRepository userRepository;
        private final FileSharedRepository fileSharedRepository;

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
                                                                        .map(shared -> shared.getExpiresAt() == null || Instant.now().isBefore(shared.getExpiresAt()))
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
        public Mono<Void> deleteByUUID(UUID uuid) {
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
                                                .then(fileRepository.deleteById(file.getId())));
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
        public Mono<UserStorageSummary> updateUserQuota(Long id, UpdateQuotaRequest request) {
                return userRepository.findById(id)
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
                                                                user.getStorageQuota())));
        }
}
