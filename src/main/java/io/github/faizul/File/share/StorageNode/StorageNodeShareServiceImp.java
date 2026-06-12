package io.github.faizul.File.share.StorageNode;

import io.github.faizul.File.share.FileShared;
import io.github.faizul.File.share.FileSharedRepository;
import io.github.faizul.File.share.ShareService;

import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.File.dtos.ShareFileRequest;
import io.github.faizul.File.dtos.ShareFileResponse;
import io.github.faizul.File.core.FileRepository;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.User.core.UserRepository;
import io.github.faizul.Storage.download.DownloadStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.NoSuchElementException;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service("storageNodeShareService")
@Transactional
@RequiredArgsConstructor
public class StorageNodeShareServiceImp implements ShareService {

    private final FileRepository fileRepository;
    private final FileSharedRepository fileSharedRepository;
    private final CurrentUserContext currentUserContext;
    private final UserRepository userRepository;
    private final DownloadStorageService downloadStorageService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public Mono<ShareFileResponse> shareFile(UUID fileId, ShareFileRequest request) {
        return currentUserContext.getUserId()
                .flatMap(userId -> fileRepository.findById(fileId)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan")))
                        .flatMap(file -> {
                            if (!"STORAGE_NODE".equals(file.getProvider())) {
                                return Mono.error(new IllegalArgumentException("Hanya berkas dari provider STORAGE_NODE yang dapat dibagikan melalui layanan ini"));
                            }
                            if (!file.getUserId().equals(userId)) {
                                return Mono.error(new AccessDeniedException("Hanya pemilik berkas yang diperbolehkan untuk membagikan berkas ini"));
                            }

                            // Hitung expiresAt
                            final Instant finalExpiresAt;
                            if ((request.expiresInDays() != null && request.expiresInDays() > 0) ||
                                    (request.expiresInHours() != null && request.expiresInHours() > 0)) {
                                long totalHours = 0;
                                if (request.expiresInDays() != null) {
                                    totalHours += request.expiresInDays() * 24L;
                                }
                                if (request.expiresInHours() != null) {
                                    totalHours += request.expiresInHours();
                                }
                                finalExpiresAt = Instant.now().plus(java.time.Duration.ofHours(totalHours));
                            } else {
                                finalExpiresAt = null;
                            }

                            if (Boolean.TRUE.equals(request.isPublic())) {
                                return fileSharedRepository.findByFileId(fileId)
                                        .filter(FileShared::getIsPublic)
                                        .next()
                                        .flatMap(existing -> {
                                            existing.setExpiresAt(finalExpiresAt);
                                            if (existing.getShareToken() == null) {
                                                existing.setShareToken(UUID.randomUUID().toString());
                                            }
                                            return fileSharedRepository.save(existing);
                                        })
                                        .switchIfEmpty(Mono.defer(() -> {
                                            String shareToken = UUID.randomUUID().toString();
                                            FileShared shared = FileShared.builder()
                                                    .fileId(fileId)
                                                    .userId(null)
                                                    .isPublic(true)
                                                    .shareToken(shareToken)
                                                    .expiresAt(finalExpiresAt)
                                                    .build();
                                            return fileSharedRepository.save(shared);
                                        }))
                                        .map(saved -> new ShareFileResponse(
                                                saved.getId(),
                                                null,
                                                true,
                                                saved.getShareToken(),
                                                frontendUrl + "/shared/public/local/" + saved.getShareToken(),
                                                saved.getExpiresAt()
                                        ));
                            } else {
                                if (request.email() == null || request.email().isBlank()) {
                                    return Mono.error(new IllegalArgumentException("Email target diperlukan untuk pembagian privat"));
                                }
                                return userRepository.findByEmail(request.email())
                                        .switchIfEmpty(Mono.error(new NoSuchElementException("Pengguna dengan alamat email tersebut tidak ditemukan")))
                                        .flatMap(targetUser -> {
                                            if (userId.equals(targetUser.getId())) {
                                                return Mono.error(new IllegalArgumentException("Anda tidak dapat membagikan berkas dengan diri Anda sendiri"));
                                            }
                                            return fileSharedRepository.findByFileIdAndUserId(fileId, targetUser.getId())
                                                    .flatMap(existing -> {
                                                        existing.setExpiresAt(finalExpiresAt);
                                                        existing.setIsPublic(false);
                                                        existing.setShareToken(null);
                                                        return fileSharedRepository.save(existing);
                                                    })
                                                    .switchIfEmpty(Mono.defer(() -> {
                                                        FileShared shared = FileShared.builder()
                                                                .fileId(fileId)
                                                                .userId(targetUser.getId())
                                                                .isPublic(false)
                                                                .expiresAt(finalExpiresAt)
                                                                .build();
                                                        return fileSharedRepository.save(shared);
                                                    }))
                                                    .map(saved -> new ShareFileResponse(
                                                            saved.getId(),
                                                            targetUser.getEmail(),
                                                            false,
                                                            null,
                                                            null,
                                                            saved.getExpiresAt()
                                                    ));
                                        });
                            }
                        }));
    }

    @Override
    public Mono<Void> unshareFile(UUID fileId, Long targetUserId) {
        return currentUserContext.getUserId()
                .flatMap(userId -> fileRepository.findById(fileId)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan")))
                        .flatMap(file -> {
                            if (!"STORAGE_NODE".equals(file.getProvider())) {
                                return Mono.error(new IllegalArgumentException("Hanya berkas dari provider STORAGE_NODE yang dapat dibatalkan pembagiannya melalui layanan ini"));
                            }
                            if (!file.getUserId().equals(userId) && !targetUserId.equals(userId)) {
                                return Mono.error(new AccessDeniedException("Anda tidak memiliki wewenang untuk membatalkan pembagian berkas ini"));
                            }
                            return fileSharedRepository.deleteByFileIdAndUserId(fileId, targetUserId);
                        }));
    }

    @Override
    public Mono<Void> unshareFile(Long shareId) {
        return currentUserContext.getUserId()
                .flatMap(userId -> fileSharedRepository.findById(shareId)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("Data pembagian tidak ditemukan")))
                        .flatMap(shared -> fileRepository.findById(shared.getFileId())
                                .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan")))
                                .flatMap(file -> {
                                    if (!"STORAGE_NODE".equals(file.getProvider())) {
                                        return Mono.error(new IllegalArgumentException("Hanya berkas dari provider STORAGE_NODE yang dapat dibatalkan pembagiannya melalui layanan ini"));
                                    }
                                    if (!file.getUserId().equals(userId) && !userId.equals(shared.getUserId())) {
                                        return Mono.error(new AccessDeniedException("Anda tidak memiliki wewenang untuk membatalkan pembagian berkas ini"));
                                    }
                                    return fileSharedRepository.deleteById(shareId);
                                })
                        ));
    }

    @Override
    public Flux<FileResponse> getSharedWithMe() {
        return currentUserContext.getUserId()
                .flatMapMany(userId -> fileSharedRepository.findByUserId(userId)
                        .flatMap(shared -> {
                            // Saring yang sudah kadaluarsa
                            if (shared.getExpiresAt() != null && Instant.now().isAfter(shared.getExpiresAt())) {
                                return Mono.empty();
                            }
                            return fileRepository.findById(shared.getFileId())
                                    .filter(file -> "STORAGE_NODE".equals(file.getProvider()))
                                    .flatMap(file -> userRepository.findById(file.getUserId())
                                            .map(owner -> new FileResponse(
                                                    file.getId(),
                                                    file.getOriginalFileName(),
                                                    file.getSize(),
                                                    file.getCreatedAt(),
                                                    file.getProvider(),
                                                    file.getExternalAccountId(),
                                                    owner.getEmail()
                                            ))
                                            .defaultIfEmpty(new FileResponse(
                                                    file.getId(),
                                                    file.getOriginalFileName(),
                                                    file.getSize(),
                                                    file.getCreatedAt(),
                                                    file.getProvider(),
                                                    file.getExternalAccountId(),
                                                    "Unknown Owner"
                                            ))
                                    );
                        })
                );
    }

    @Override
    public Mono<Boolean> hasReadAccess(UUID fileId, Long userId) {
        return fileRepository.findById(fileId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("File Not Found")))
                .flatMap(file -> {
                    if (!"STORAGE_NODE".equals(file.getProvider())) {
                        return Mono.just(false);
                    }
                    if (file.getUserId().equals(userId)) {
                        return Mono.just(true);
                    }
                    return fileSharedRepository.findByFileIdAndUserId(fileId, userId)
                            .map(shared -> shared.getExpiresAt() == null || Instant.now().isBefore(shared.getExpiresAt()))
                            .defaultIfEmpty(false);
                });
    }

    @Override
    public Mono<FileResponse> getPublicFileInfo(String shareToken) {
        return fileSharedRepository.findByShareToken(shareToken)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Tautan pembagian tidak ditemukan")))
                .flatMap(shared -> {
                    if (shared.getExpiresAt() != null && Instant.now().isAfter(shared.getExpiresAt())) {
                        return Mono.error(new AccessDeniedException("Tautan pembagian telah kadaluarsa"));
                    }
                    return fileRepository.findById(shared.getFileId())
                            .filter(file -> "STORAGE_NODE".equals(file.getProvider()))
                            .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan")))
                            .flatMap(file -> userRepository.findById(file.getUserId())
                                    .map(owner -> new FileResponse(
                                            file.getId(),
                                            file.getOriginalFileName(),
                                            file.getSize(),
                                            file.getCreatedAt(),
                                            file.getProvider(),
                                            file.getExternalAccountId(),
                                            owner.getEmail()
                                    ))
                                    .defaultIfEmpty(new FileResponse(
                                            file.getId(),
                                            file.getOriginalFileName(),
                                            file.getSize(),
                                            file.getCreatedAt(),
                                            file.getProvider(),
                                            file.getExternalAccountId(),
                                            "Unknown Owner"
                                    ))
                            );
                });
    }

    @Override
    public Flux<byte[]> downloadPublicFile(String shareToken) {
        return fileSharedRepository.findByShareToken(shareToken)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Tautan pembagian tidak ditemukan")))
                .flatMapMany(shared -> {
                    if (shared.getExpiresAt() != null && Instant.now().isAfter(shared.getExpiresAt())) {
                        return Flux.error(new AccessDeniedException("Tautan pembagian telah kadaluarsa"));
                    }
                    return fileRepository.findById(shared.getFileId())
                            .filter(file -> "STORAGE_NODE".equals(file.getProvider()))
                            .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan")))
                            .flatMapMany(file -> downloadStorageService.downloadFile(file.getUserId(), file.getId())
                                    .map(chunk -> chunk.data()));
                });
    }

    @Override
    public Flux<io.github.faizul.File.dtos.SharedByMeResponse> getSharedByMe() {
        return currentUserContext.getUserId()
                .flatMapMany(userId -> fileRepository.findByUserId(userId)
                        .filter(file -> "STORAGE_NODE".equals(file.getProvider()))
                        .flatMap(file -> fileSharedRepository.findByFileId(file.getId())
                                .flatMap(shared -> {
                                    if (Boolean.TRUE.equals(shared.getIsPublic())) {
                                        return Mono.just(new io.github.faizul.File.dtos.SharedByMeResponse(
                                                shared.getId(),
                                                file.getId(),
                                                file.getOriginalFileName(),
                                                file.getSize(),
                                                shared.getCreatedAt() != null ? shared.getCreatedAt() : file.getCreatedAt(),
                                                file.getProvider(),
                                                true,
                                                shared.getShareToken(),
                                                frontendUrl + "/shared/public/local/" + shared.getShareToken(),
                                                shared.getExpiresAt(),
                                                null
                                        ));
                                    } else {
                                        return userRepository.findById(shared.getUserId())
                                                .map(targetUser -> new io.github.faizul.File.dtos.SharedByMeResponse(
                                                        shared.getId(),
                                                        file.getId(),
                                                        file.getOriginalFileName(),
                                                        file.getSize(),
                                                        shared.getCreatedAt() != null ? shared.getCreatedAt() : file.getCreatedAt(),
                                                        file.getProvider(),
                                                        false,
                                                        null,
                                                        null,
                                                        shared.getExpiresAt(),
                                                        targetUser.getEmail()
                                                ))
                                                .defaultIfEmpty(new io.github.faizul.File.dtos.SharedByMeResponse(
                                                        shared.getId(),
                                                        file.getId(),
                                                        file.getOriginalFileName(),
                                                        file.getSize(),
                                                        shared.getCreatedAt() != null ? shared.getCreatedAt() : file.getCreatedAt(),
                                                        file.getProvider(),
                                                        false,
                                                        null,
                                                        null,
                                                        shared.getExpiresAt(),
                                                        "Unknown User"
                                                ));
                                    }
                                })
                        )
                );
    }
}
