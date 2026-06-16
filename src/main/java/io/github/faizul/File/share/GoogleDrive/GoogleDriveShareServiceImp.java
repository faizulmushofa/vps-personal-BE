package io.github.faizul.File.share.GoogleDrive;

import io.github.faizul.File.share.FileShared;
import io.github.faizul.File.share.FileSharedRepository;
import io.github.faizul.File.share.ShareService;

import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.File.dtos.ShareFileRequest;
import io.github.faizul.File.dtos.ShareFileResponse;
import io.github.faizul.File.core.FileRepository;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.User.core.UserRepository;
import io.github.faizul.User.core.User;
import io.github.faizul.File.core.googleDrive.GoogleDriveClient;
import io.github.faizul.User.externalAccount.ExternalAccountRepository;
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

@Service("googleDriveShareService")
@Transactional
@RequiredArgsConstructor
public class GoogleDriveShareServiceImp implements ShareService {

    private final FileRepository fileRepository;
    private final FileSharedRepository fileSharedRepository;
    private final CurrentUserContext currentUserContext;
    private final UserRepository userRepository;
    private final GoogleDriveClient googleDriveClient;
    private final ExternalAccountRepository externalAccountRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public Mono<ShareFileResponse> shareFile(String fileId, ShareFileRequest request) {
        return currentUserContext.getUserId()
                .flatMap(userId -> {
                    Mono<io.github.faizul.File.core.File> fileMono;
                    try {
                        UUID uuid = UUID.fromString(fileId);
                        fileMono = fileRepository.findById(uuid);
                    } catch (IllegalArgumentException e) {
                        // Jika bukan UUID, cari berdasarkan storageName (id google drive aslinya) dan provider = GOOGLE_DRIVE
                        fileMono = fileRepository.findByStorageNameAndProvider(fileId, "GOOGLE_DRIVE")
                                .switchIfEmpty(Mono.defer(() -> {
                                    // Jika tidak ada di DB lokal, cari dari API Google Drive dan daftarkan
                                    return externalAccountRepository.findAllByUserId(userId)
                                            .filter(acc -> "GOOGLE".equalsIgnoreCase(acc.getProvider()) || "GOOGLE_DRIVE".equalsIgnoreCase(acc.getProvider()))
                                            .next()
                                            .flatMap(acc -> googleDriveClient.getFileMetadata(acc.getId(), fileId)
                                                    .flatMap(meta -> {
                                                        String name = (String) meta.get("name");
                                                        Long size = meta.get("size") != null ? Long.parseLong(meta.get("size").toString()) : 0L;
                                                        
                                                        io.github.faizul.File.core.File newFile = io.github.faizul.File.core.File.builder()
                                                                .id(UUID.randomUUID())
                                                                .userId(userId)
                                                                .originalFileName(name)
                                                                .storageName(fileId) // ID GDrive
                                                                .size(size)
                                                                .provider("GOOGLE_DRIVE")
                                                                .externalAccountId(acc.getId())
                                                                .createdAt(Instant.now())
                                                                .build();
                                                        return fileRepository.save(newFile);
                                                    })
                                            );
                                }));
                    }

                    return fileMono
                            .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas Google Drive tidak ditemukan")))
                            .flatMap(file -> {
                                if (!"GOOGLE_DRIVE".equals(file.getProvider())) {
                                    return Mono.error(new IllegalArgumentException("Hanya berkas dari provider GOOGLE_DRIVE yang dapat dibatalkan pembagiannya melalui layanan ini"));
                                }
                                if (!file.getUserId().equals(userId)) {
                                    return Mono.error(new AccessDeniedException("Hanya pemilik berkas yang diperbolehkan untuk membagikan berkas ini"));
                                }

                                final UUID fileUUID = file.getId();

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

                                return userRepository.findById(userId)
                                        .switchIfEmpty(Mono.error(new NoSuchElementException("User Not Found")))
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
                                        .flatMap(user -> {
                                            return fileRepository.calculateUsedStorageByUserId(userId)
                                                    .defaultIfEmpty(0L)
                                                    .flatMap(usedStorage -> {
                                                        long quota = user.getStorageQuota() != null ? user.getStorageQuota() : 1073741824L;
                                                        if (usedStorage > quota) {
                                                            return Mono.error(new IllegalArgumentException(
                                                                    "Kapasitas penyimpanan Anda penuh. Fitur berbagi dinonaktifkan."));
                                                        }

                                                        if (Boolean.TRUE.equals(request.isPublic())) {
                                                            return fileSharedRepository.findByFileId(fileUUID)
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
                                                                        int limit = user.getSubscriptionPlan().getLimits().publicShareLimit();
                                                                        Mono<Void> limitCheck = Mono.empty();
                                                                        if (limit != -1) {
                                                                            limitCheck = fileSharedRepository.countActivePublicSharesByOwnerId(userId)
                                                                                    .flatMap(count -> {
                                                                                        if (count >= limit) {
                                                                                            return Mono.error(new IllegalArgumentException("Batas link share publik aktif untuk paket Anda (" + limit + ") telah tercapai."));
                                                                                        }
                                                                                        return Mono.empty();
                                                                                    });
                                                                        }
                                                                        return limitCheck.then(Mono.defer(() -> {
                                                                            String shareToken = UUID.randomUUID().toString();
                                                                            FileShared shared = FileShared.builder()
                                                                                    .fileId(fileUUID)
                                                                                    .userId(null)
                                                                                    .isPublic(true)
                                                                                    .shareToken(shareToken)
                                                                                    .expiresAt(finalExpiresAt)
                                                                                    .build();
                                                                            return fileSharedRepository.save(shared);
                                                                        }));
                                                                    }))
                                                                    .map(saved -> new ShareFileResponse(
                                                                            saved.getId(),
                                                                            null,
                                                                            true,
                                                                            saved.getShareToken(),
                                                                            frontendUrl + "/shared/public/google/" + saved.getShareToken(),
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
                                                                        return fileSharedRepository.findByFileIdAndUserId(fileUUID, targetUser.getId())
                                                                                .flatMap(existing -> {
                                                                                    existing.setExpiresAt(finalExpiresAt);
                                                                                    existing.setIsPublic(false);
                                                                                    existing.setShareToken(null);
                                                                                    return fileSharedRepository.save(existing);
                                                                                })
                                                                                .switchIfEmpty(Mono.defer(() -> {
                                                                                    int limit = user.getSubscriptionPlan().getLimits().privateShareLimit();
                                                                                    Mono<Void> limitCheck = Mono.empty();
                                                                                    if (limit != -1) {
                                                                                        limitCheck = fileSharedRepository.countActivePrivateSharesByOwnerId(userId)
                                                                                                .flatMap(count -> {
                                                                                                    if (count >= limit) {
                                                                                                        return Mono.error(new IllegalArgumentException("Batas share privat aktif untuk paket Anda (" + limit + ") telah tercapai."));
                                                                                                    }
                                                                                                    return Mono.empty();
                                                                                                });
                                                                                    }
                                                                                    return limitCheck.then(Mono.defer(() -> {
                                                                                        FileShared shared = FileShared.builder()
                                                                                                .fileId(fileUUID)
                                                                                                .userId(targetUser.getId())
                                                                                                .isPublic(false)
                                                                                                .expiresAt(finalExpiresAt)
                                                                                                .build();
                                                                                        return fileSharedRepository.save(shared);
                                                                                    }));
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
                                                    });
                                        });
                            });
                });
    }

    @Override
    public Mono<Void> unshareFile(String fileId, Long targetUserId) {
        UUID fileUUID = UUID.fromString(fileId);
        return currentUserContext.getUserId()
                .flatMap(userId -> fileRepository.findById(fileUUID)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan")))
                        .flatMap(file -> {
                            if (!"GOOGLE_DRIVE".equals(file.getProvider())) {
                                return Mono.error(new IllegalArgumentException("Hanya berkas dari provider GOOGLE_DRIVE yang dapat dibatalkan pembagiannya melalui layanan ini"));
                            }
                            if (!file.getUserId().equals(userId) && !targetUserId.equals(userId)) {
                                return Mono.error(new AccessDeniedException("Anda tidak memiliki wewenang untuk membatalkan pembagian berkas ini"));
                            }
                            return fileSharedRepository.deleteByFileIdAndUserId(fileUUID, targetUserId);
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
                                    if (!"GOOGLE_DRIVE".equals(file.getProvider())) {
                                        return Mono.error(new IllegalArgumentException("Hanya berkas dari provider GOOGLE_DRIVE yang dapat dibatalkan pembagiannya melalui layanan ini"));
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
                                    .filter(file -> "GOOGLE_DRIVE".equals(file.getProvider()))
                                    .flatMap(file -> userRepository.findById(file.getUserId())
                                            .map(owner -> new FileResponse(
                                                    file.getId().toString(),
                                                    file.getOriginalFileName(),
                                                    file.getSize(),
                                                    file.getCreatedAt(),
                                                    file.getProvider(),
                                                    file.getExternalAccountId(),
                                                    owner.getEmail()
                                            ))
                                            .defaultIfEmpty(new FileResponse(
                                                    file.getId().toString(),
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
    public Mono<Boolean> hasReadAccess(String fileId, Long userId) {
        UUID fileUUID = UUID.fromString(fileId);
        return fileRepository.findById(fileUUID)
                .switchIfEmpty(Mono.error(new NoSuchElementException("File Not Found")))
                .flatMap(file -> {
                    if (!"GOOGLE_DRIVE".equals(file.getProvider())) {
                        return Mono.just(false);
                    }
                    if (file.getUserId().equals(userId)) {
                        return Mono.just(true);
                    }
                    return fileSharedRepository.findByFileIdAndUserId(fileUUID, userId)
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
                            .filter(file -> "GOOGLE_DRIVE".equals(file.getProvider()))
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
                            .filter(file -> "GOOGLE_DRIVE".equals(file.getProvider()))
                            .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan")))
                            .flatMapMany(file -> googleDriveClient.downloadFile(file.getExternalAccountId(), file.getStorageName()));
                });
    }

    @Override
    public Flux<io.github.faizul.File.dtos.SharedByMeResponse> getSharedByMe() {
        return currentUserContext.getUserId()
                .flatMapMany(userId -> fileRepository.findByUserId(userId)
                        .filter(file -> "GOOGLE_DRIVE".equals(file.getProvider()))
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
                                                frontendUrl + "/shared/public/google/" + shared.getShareToken(),
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
