package io.github.faizul.storage.share.local.service.impl;

import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.infra.config.StorageConfig;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.storage.file.dtos.FileResponse;
import io.github.faizul.storage.file.model.File;
import io.github.faizul.storage.file.repository.FileRepository;
import io.github.faizul.storage.file.service.client.GoogleDriveClient;
import io.github.faizul.storage.folder.dtos.FolderContentResponse;
import io.github.faizul.storage.folder.dtos.FolderResponse;
import io.github.faizul.storage.folder.model.Folder;
import io.github.faizul.storage.folder.repository.FolderRepository;
import io.github.faizul.storage.share.dtos.*;
import io.github.faizul.storage.share.dtos.ShareFolderRequest;
import io.github.faizul.storage.share.dtos.SharedFolderResponse;
import io.github.faizul.storage.share.dtos.UpdateShareAccessRequest;
import io.github.faizul.storage.share.dtos.UpdateShareExpiryRequest;
import io.github.faizul.storage.share.model.FolderShared;
import io.github.faizul.storage.share.repository.FolderSharedRepository;
import io.github.faizul.storage.download.service.DownloadStorageService;
import io.github.faizul.storage.share.local.service.FolderSharedService;
import io.github.faizul.storage.upload.service.UploadStorageService;
import io.github.faizul.user.repository.ExternalAccountRepository;
import io.github.faizul.user.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;



@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FolderSharedServiceImpl implements FolderSharedService {

    private final FolderSharedRepository folderSharedRepository;
    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final CurrentUserContext currentUserContext;
    private final UserActivityService userActivityService;
    private final GoogleDriveClient googleDriveClient;
    private final ExternalAccountRepository externalAccountRepository;
    private final StorageConfig storageConfig;
    private final UploadStorageService uploadStorageClient;
    private final DownloadStorageService downloadStorageService;

    private final java.util.Map<String, java.util.List<Long>> ipUploadTimestamps = new ConcurrentHashMap<>();

    @Override
    public Mono<SharedFolderResponse> shareFolder(ShareFolderRequest request, ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> {
                    String shareToken = UUID.randomUUID().toString();
                    
                    Mono<String> getTargetStorageAndAccount = Mono.defer(() -> {
                        if ("GOOGLE_DRIVE".equalsIgnoreCase(request.folderType())) {
                            return externalAccountRepository.findAllByUserId(userId)
                                    .filter(acc -> "GOOGLE".equalsIgnoreCase(acc.getProvider()) || "GOOGLE_DRIVE".equalsIgnoreCase(acc.getProvider()))
                                    .next()
                                    .map(acc -> "GOOGLE_DRIVE:" + acc.getId())
                                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Akun Google Drive tidak terhubung.")));
                        } else {
                            return Mono.just("LOCAL:");
                        }
                    });

                    return getTargetStorageAndAccount.flatMap(storageInfo -> {
                        String[] parts = storageInfo.split(":");
                        String targetStorage = parts[0];
                        Long extAccId = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : null;

                          FolderShared folderShared = FolderShared.builder()
                                 .folderId(request.folderId())
                                 .folderType(request.folderType())
                                 .userId(userId)
                                 .expiresAt(request.expiresAt() != null ? LocalDateTime.ofInstant(request.expiresAt(), java.time.ZoneOffset.UTC) : null)
                                 .shareToken(shareToken)
                                 .permission(request.permission())
                                 .allowAnonymous(request.allowAnonymous() != null ? request.allowAnonymous() : true)
                                 .targetStorage(targetStorage)
                                 .externalAccountId(extAccId)
                                 .build();
 
                          return folderSharedRepository.save(folderShared)
                                  .flatMap(saved -> userActivityService.log(
                                          userId,
                                          "SHARE_FOLDER_CREATE",
                                          "Membagikan folder " + request.folderId() + " dengan permission " + request.permission(),
                                          exchange
                                  ).then(resolveFolderName(saved.getFolderId(), saved.getFolderType(), userId, extAccId)
                                          .map(folderName -> new SharedFolderResponse(
                                                  saved.getId(),
                                                  saved.getFolderId(),
                                                  saved.getFolderType(),
                                                  saved.getShareToken(),
                                                  saved.getPermission(),
                                                  saved.getAllowAnonymous(),
                                                  saved.getExpiresAt() != null ? saved.getExpiresAt().toInstant(java.time.ZoneOffset.UTC) : null,
                                                  saved.getCreatedAt(),
                                                  folderName
                                          ))));
                     });
                 });
    }

    @Override
    public Mono<SharedFolderResponse> updateExpiry(String shareToken, UpdateShareExpiryRequest request, ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> folderSharedRepository.findByShareToken(shareToken)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("Link share tidak ditemukan.")))
                        .flatMap(shared -> {
                            if (!shared.getUserId().equals(userId)) {
                                return Mono.error(new AccessDeniedException("Anda tidak memiliki akses ke link share ini."));
                            }
                             shared.setExpiresAt(request.expiresAt() != null ? LocalDateTime.ofInstant(request.expiresAt(), java.time.ZoneOffset.UTC) : null);
                             return folderSharedRepository.save(shared)
                                     .flatMap(saved -> userActivityService.log(
                                             userId,
                                             "SHARE_FOLDER_EXPIRY_UPDATE",
                                             "Memperbarui kedaluwarsa share link folder " + saved.getFolderId(),
                                             exchange
                                     ).then(resolveFolderName(saved.getFolderId(), saved.getFolderType(), userId, saved.getExternalAccountId())
                                             .map(folderName -> new SharedFolderResponse(
                                                     saved.getId(),
                                                     saved.getFolderId(),
                                                     saved.getFolderType(),
                                                     saved.getShareToken(),
                                                     saved.getPermission(),
                                                     saved.getAllowAnonymous(),
                                                     saved.getExpiresAt() != null ? saved.getExpiresAt().toInstant(java.time.ZoneOffset.UTC) : null,
                                                     saved.getCreatedAt(),
                                                     folderName
                                             ))));
                        }));
    }

    @Override
    public Mono<SharedFolderResponse> updateAccess(String shareToken, UpdateShareAccessRequest request, ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> folderSharedRepository.findByShareToken(shareToken)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("Link share tidak ditemukan.")))
                        .flatMap(shared -> {
                            if (!shared.getUserId().equals(userId)) {
                                return Mono.error(new AccessDeniedException("Anda tidak memiliki akses ke link share ini."));
                            }
                            shared.setPermission(request.permission());
                            shared.setAllowAnonymous(request.allowAnonymous());
                            return folderSharedRepository.save(shared)
                                    .flatMap(saved -> userActivityService.log(
                                            userId,
                                            "SHARE_FOLDER_UPDATE",
                                            "Memperbarui hak akses folder " + saved.getFolderId() + " menjadi " + request.permission(),
                                            exchange
                                    ).then(resolveFolderName(saved.getFolderId(), saved.getFolderType(), userId, saved.getExternalAccountId())
                                            .map(folderName -> new SharedFolderResponse(
                                                    saved.getId(),
                                                    saved.getFolderId(),
                                                    saved.getFolderType(),
                                                    saved.getShareToken(),
                                                    saved.getPermission(),
                                                    saved.getAllowAnonymous(),
                                                    saved.getExpiresAt() != null ? saved.getExpiresAt().toInstant(java.time.ZoneOffset.UTC) : null,
                                                    saved.getCreatedAt(),
                                                    folderName
                                            ))));
                        }));
    }

    @Override
    public Mono<Void> revokeShare(String shareToken, ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> folderSharedRepository.findByShareToken(shareToken)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("Link share tidak ditemukan.")))
                        .flatMap(shared -> {
                            if (!shared.getUserId().equals(userId)) {
                                return Mono.error(new AccessDeniedException("Anda tidak memiliki akses ke link share ini."));
                            }
                            return folderSharedRepository.delete(shared)
                                    .then(userActivityService.log(
                                            userId,
                                            "SHARE_FOLDER_REVOKE",
                                            "Membatalkan share folder " + shared.getFolderId(),
                                            exchange
                                    )).then();
                        }));
    }

    @Override
    @Transactional
    public Flux<SharedFolderResponse> getSharedFoldersByMe() {
        return currentUserContext.getUserId()
                .flatMapMany(folderSharedRepository::findByUserId)
                .flatMap(saved -> resolveFolderName(saved.getFolderId(), saved.getFolderType(), saved.getUserId(), saved.getExternalAccountId())
                        .map(folderName -> new SharedFolderResponse(
                                saved.getId(),
                                saved.getFolderId(),
                                saved.getFolderType(),
                                saved.getShareToken(),
                                saved.getPermission(),
                                saved.getAllowAnonymous(),
                                saved.getExpiresAt() != null ? saved.getExpiresAt().toInstant(java.time.ZoneOffset.UTC) : null,
                                saved.getCreatedAt(),
                                folderName
                        )));
    }

    @Override
    @Transactional
    public Mono<FolderContentResponse> getSharedFolderContentsPublic(String shareToken, String folderId, ServerWebExchange exchange) {
        return folderSharedRepository.findByShareToken(shareToken)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Link share tidak ditemukan atau tidak valid.")))
                .flatMap(shared -> {
                    if (shared.getExpiresAt() != null && LocalDateTime.now(java.time.ZoneOffset.UTC).isAfter(shared.getExpiresAt())) {
                        return Mono.error(new IllegalArgumentException("Tautan berbagi folder telah kedaluwarsa."));
                    }

                    String targetFolderId = (folderId == null || folderId.trim().isEmpty()) ? shared.getFolderId() : folderId;

                    if ("LOCAL".equalsIgnoreCase(shared.getFolderType())) {
                        UUID parentUUID = UUID.fromString(targetFolderId);
                        UUID rootUUID = UUID.fromString(shared.getFolderId());

                        return isDescendant(parentUUID, rootUUID, shared.getUserId())
                                .flatMap(isOk -> {
                                    if (!isOk) {
                                        return Mono.error(new AccessDeniedException("Akses ke subfolder ini ditolak."));
                                    }
                                    return resolveFolderName(targetFolderId, shared.getFolderType(), shared.getUserId(), shared.getExternalAccountId())
                                            .flatMap(folderName -> Mono.zip(
                                                    folderRepository.findByUserIdAndParentId(shared.getUserId(), parentUUID)
                                                            .map(f -> new FolderResponse(f.getId().toString(), f.getName(), f.getParentId() != null ? f.getParentId().toString() : null, f.getUserId(), f.getCreatedAt()))
                                                            .collectList()
                                                            .defaultIfEmpty(new ArrayList<>()),
                                                    fileRepository.findByUserIdAndFolderIdAndProvider(shared.getUserId(), parentUUID, "STORAGE_NODE")
                                                            .map(file -> new FileResponse(
                                                                    file.getId().toString(),
                                                                    file.getOriginalFileName(),
                                                                    file.getSize(),
                                                                    file.getCreatedAt(),
                                                                    file.getProvider(),
                                                                    file.getExternalAccountId(),
                                                                    null
                                                            ))
                                                            .collectList()
                                                            .defaultIfEmpty(new ArrayList<>())
                                            ).map(tuple -> new FolderContentResponse(tuple.getT1(), tuple.getT2(), shared.getPermission(), shared.getAllowAnonymous(), folderName)));
                                });
                    } else if ("GOOGLE_DRIVE".equalsIgnoreCase(shared.getFolderType())) {
                        return resolveFolderName(targetFolderId, shared.getFolderType(), shared.getUserId(), shared.getExternalAccountId())
                                .flatMap(folderName -> googleDriveClient.listFilesAndFolders(shared.getExternalAccountId(), targetFolderId)
                                        .map(list -> {
                                            List<FolderResponse> folders = new ArrayList<>();
                                            List<FileResponse> files = new ArrayList<>();
                                            for (java.util.Map<String, Object> map : list) {
                                                String id = (String) map.get("id");
                                                String name = (String) map.get("name");
                                                String mimeType = (String) map.get("mimeType");
                                                Long size = map.get("size") != null ? Long.parseLong(map.get("size").toString()) : 0L;
                                                
                                                Instant createdTime = Instant.now();
                                                if (map.get("createdTime") != null) {
                                                    createdTime = Instant.parse(map.get("createdTime").toString());
                                                }

                                                if ("application/vnd.google-apps.folder".equals(mimeType)) {
                                                    folders.add(new FolderResponse(id, name, targetFolderId, shared.getUserId(), createdTime));
                                                } else {
                                                    files.add(new FileResponse(id, name, size, createdTime, "GOOGLE_DRIVE", shared.getExternalAccountId(), null));
                                                }
                                            }
                                            return new FolderContentResponse(folders, files, shared.getPermission(), shared.getAllowAnonymous(), folderName);
                                        }));
                    } else {
                        return Mono.error(new IllegalArgumentException("Tipe folder tidak valid: " + shared.getFolderType()));
                    }
                });
    }

    @Override
    public Mono<FileResponse> uploadToSharedFolderPublic(
            String shareToken, 
            String folderId,
            String fileName, 
            long size, 
            FilePart filePart, 
            ServerWebExchange exchange) {
        
        return folderSharedRepository.findByShareToken(shareToken)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Link share tidak ditemukan.")))
                .flatMap(shared -> {
                    // 1. Cek expiry
                    if (shared.getExpiresAt() != null && LocalDateTime.now(java.time.ZoneOffset.UTC).isAfter(shared.getExpiresAt())) {
                        return Mono.error(new IllegalArgumentException("Tautan berbagi folder telah kedaluwarsa."));
                    }

                    // 2. Cek permission
                    if (!"EDIT".equalsIgnoreCase(shared.getPermission())) {
                        return Mono.error(new AccessDeniedException("Anda tidak memiliki izin mengunggah ke folder ini."));
                    }

                    String targetFolderId = (folderId == null || folderId.trim().isEmpty()) ? shared.getFolderId() : folderId;

                    // 3. Cek rate limit
                    return checkRateLimit(exchange)
                            .then(currentUserContext.getUserId()
                                    .map(userId -> true)
                                    .defaultIfEmpty(false)
                                    .flatMap(isLoggedIn -> {
                                        // 4. Cek allow anonymous
                                        if (Boolean.FALSE.equals(shared.getAllowAnonymous()) && !isLoggedIn) {
                                            return Mono.error(new AccessDeniedException("Unggahan anonim dinonaktifkan. Anda wajib login untuk mengunggah berkas."));
                                        }
                                        return Mono.just(isLoggedIn);
                                    })
                                    .flatMap(isLoggedIn -> validateStorageQuota(shared, size)
                                            .then(processPublicUpload(shared, targetFolderId, fileName, size, filePart, isLoggedIn, exchange))
                                    )
                            );
                });
    }

    @Override
    public Mono<Void> deleteFromSharedFolderPublic(String shareToken, String fileId, ServerWebExchange exchange) {
        return folderSharedRepository.findByShareToken(shareToken)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Link share tidak ditemukan.")))
                .flatMap(shared -> {
                    if (shared.getExpiresAt() != null && LocalDateTime.now(java.time.ZoneOffset.UTC).isAfter(shared.getExpiresAt())) {
                        return Mono.error(new IllegalArgumentException("Tautan berbagi folder telah kedaluwarsa."));
                    }
                    if (!"EDIT".equalsIgnoreCase(shared.getPermission())) {
                        return Mono.error(new AccessDeniedException("Anda tidak memiliki izin menghapus berkas di folder ini."));
                    }

                    return currentUserContext.getUserId()
                            .map(userId -> true)
                            .defaultIfEmpty(false)
                            .flatMap(isLoggedIn -> {
                                if (Boolean.FALSE.equals(shared.getAllowAnonymous()) && !isLoggedIn) {
                                    return Mono.error(new AccessDeniedException("Penghapusan anonim dinonaktifkan. Anda wajib login terlebih dahulu."));
                                }
                                
                                if ("LOCAL".equalsIgnoreCase(shared.getFolderType())) {
                                    UUID fileUUID = UUID.fromString(fileId);
                                    return fileRepository.findById(fileUUID)
                                            .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan.")))
                                            .flatMap(file -> {
                                                UUID rootUUID = UUID.fromString(shared.getFolderId());
                                                if (file.getFolderId() == null) {
                                                    return Mono.error(new IllegalArgumentException("Berkas tidak berada di dalam folder bersama ini."));
                                                }
                                                return isDescendant(file.getFolderId(), rootUUID, shared.getUserId())
                                                        .flatMap(isOk -> {
                                                            if (!isOk) {
                                                                return Mono.error(new IllegalArgumentException("Berkas tidak berada di dalam folder bersama ini."));
                                                            }
                                                            return uploadStorageClient.deleteFile(shared.getUserId(), file.getId().toString())
                                                                    .onErrorResume(e -> Mono.empty())
                                                                    .then(fileRepository.deleteById(file.getId()))
                                                                    .then(logPublicActivity(isLoggedIn, "ANONYMOUS_DELETE", "Menghapus berkas '" + file.getOriginalFileName() + "' secara anonim pada folder bersama.", exchange));
                                                        });
                                            }).then();
                                } else {
                                    // Google Drive physical file deletion with recursive descendant validation
                                    return isGoogleDriveDescendant(fileId, shared.getFolderId(), shared.getExternalAccountId())
                                            .flatMap(isOk -> {
                                                if (!isOk) {
                                                    return Mono.error(new IllegalArgumentException("Berkas tidak berada di dalam folder bersama ini."));
                                                }
                                                return googleDriveClient.deleteFile(shared.getExternalAccountId(), fileId)
                                                        .then(fileRepository.findByStorageNameAndProvider(fileId, "GOOGLE_DRIVE")
                                                                .flatMap(f -> fileRepository.deleteById(f.getId()))
                                                                .onErrorResume(e -> Mono.empty())
                                                        )
                                                        .then(logPublicActivity(isLoggedIn, "ANONYMOUS_DELETE", "Menghapus berkas GDrive secara anonim pada folder bersama.", exchange));
                                            });
                                }
                            });
                });
    }

    private Mono<Void> validateStorageQuota(FolderShared shared, long fileSize) {
        if ("LOCAL".equalsIgnoreCase(shared.getTargetStorage())) {
            return userRepository.findById(shared.getUserId())
                    .switchIfEmpty(Mono.error(new NoSuchElementException("Pemilik folder tidak ditemukan.")))
                    .flatMap(user -> fileRepository.calculateUsedStorageByUserId(shared.getUserId())
                             .flatMap(usedBytes -> {
                                 long quota = user.getStorageQuota() != null ? user.getStorageQuota() : 1073741824L;
                                 if (usedBytes + fileSize > quota) {
                                     return Mono.error(new IllegalArgumentException("Kapasitas penyimpanan pemilik folder telah habis / tidak mencukupi."));
                                 }
                                 return Mono.empty();
                             }));
        } else {
            return googleDriveClient.getAboutSpace(shared.getExternalAccountId())
                    .flatMap(quotaMap -> {
                        long gUsed = 0L;
                        long gLimit = 0L;
                        if (quotaMap.get("usage") != null) {
                            gUsed = Long.parseLong(quotaMap.get("usage").toString());
                        }
                        if (quotaMap.get("limit") != null) {
                            gLimit = Long.parseLong(quotaMap.get("limit").toString());
                        }
                        if (gLimit > 0 && gUsed + fileSize > gLimit) {
                            return Mono.error(new IllegalArgumentException("Kapasitas penyimpanan Google Drive pemilik folder tidak mencukupi."));
                        }
                        return Mono.empty();
                    });
        }
    }

    private Mono<FileResponse> processPublicUpload(
            FolderShared shared, 
            String targetFolderId,
            String fileName, 
            long size, 
            FilePart filePart, 
            boolean isLoggedIn, 
            ServerWebExchange exchange) {
        
        UUID fileId = UUID.randomUUID();
        Path tempDir = storageConfig.tempDir(shared.getUserId(), fileId);
        
        String extension = "";
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot != -1) {
            extension = fileName.substring(lastDot);
        }
        String storageName = UUID.randomUUID() + extension;

        if ("LOCAL".equalsIgnoreCase(shared.getTargetStorage())) {
            UUID parentUUID = UUID.fromString(targetFolderId);
            UUID rootUUID = UUID.fromString(shared.getFolderId());

            return isDescendant(parentUUID, rootUUID, shared.getUserId())
                    .flatMap(isOk -> {
                        if (!isOk) {
                            return Mono.error(new AccessDeniedException("Akses ke folder tujuan ditolak."));
                        }
                        // Local VPS storage upload
                        return writeChunks(filePart, tempDir)
                                .flatMap(totalChunks -> uploadStorageClient.sendBatch(shared.getUserId(), fileId, 0, totalChunks - 1)
                                        .then(uploadStorageClient.sendFinalSignal(shared.getUserId(), fileId, totalChunks))
                                        .then(Mono.defer(() -> {
                                            File file = File.builder()
                                                    .id(fileId)
                                                    .userId(shared.getUserId())
                                                    .originalFileName(fileName)
                                                    .storageName(storageName)
                                                    .size(size)
                                                    .provider("STORAGE_NODE")
                                                    .folderId(parentUUID)
                                                    .build();

                                            return fileRepository.save(file)
                                                    .flatMap(saved -> logPublicActivity(isLoggedIn, "ANONYMOUS_UPLOAD", "Mengunggah berkas '" + fileName + "' ke folder bersama secara anonim.", exchange)
                                                            .thenReturn(new FileResponse(
                                                                    saved.getId().toString(),
                                                                    saved.getOriginalFileName(),
                                                                    saved.getSize(),
                                                                    saved.getCreatedAt(),
                                                                    saved.getProvider(),
                                                                    saved.getExternalAccountId(),
                                                                    null
                                                            )));
                                        }))
                                );
                    })
                    .doFinally(signal -> {
                        try {
                            org.springframework.util.FileSystemUtils.deleteRecursively(tempDir);
                        } catch (IOException e) {
                            log.warn("Failed to cleanup public upload temp dir: {}", e.getMessage());
                        }
                    });
        } else {
            // Google Drive Storage upload
            Path tempFile = tempDir.resolve(fileName);
            return writeDirectFile(filePart, tempFile)
                    .flatMap(path -> {
                        String mimeType = detectMimeType(path);
                        return googleDriveClient.uploadFile(shared.getExternalAccountId(), path, fileName, mimeType, targetFolderId)
                                .flatMap(googleFileId -> {
                                    File file = File.builder()
                                            .id(fileId)
                                            .userId(shared.getUserId())
                                            .originalFileName(fileName)
                                            .storageName(googleFileId)
                                            .size(size)
                                            .provider("GOOGLE_DRIVE")
                                            .externalAccountId(shared.getExternalAccountId())
                                            .build();

                                    return fileRepository.save(file)
                                            .flatMap(saved -> logPublicActivity(isLoggedIn, "ANONYMOUS_UPLOAD", "Mengunggah berkas '" + fileName + "' ke Google Drive folder bersama secara anonim.", exchange)
                                                    .thenReturn(new FileResponse(
                                                            googleFileId,
                                                            saved.getOriginalFileName(),
                                                            saved.getSize(),
                                                            saved.getCreatedAt(),
                                                            saved.getProvider(),
                                                            saved.getExternalAccountId(),
                                                            null
                                                    )));
                                });
                    })
                    .doFinally(signal -> {
                        try {
                            org.springframework.util.FileSystemUtils.deleteRecursively(tempDir);
                        } catch (IOException e) {
                            log.warn("Failed to cleanup public upload temp dir: {}", e.getMessage());
                        }
                    });
        }
    }

    private Mono<Void> logPublicActivity(boolean isLoggedIn, String activityType, String desc, ServerWebExchange exchange) {
        if (isLoggedIn) {
            return currentUserContext.getUserId()
                    .flatMap(userId -> userActivityService.log(userId, activityType, desc, exchange))
                    .then();
        } else {
            return userActivityService.log(null, activityType, desc, exchange).then();
        }
    }

    private Mono<Integer> writeChunks(FilePart filePart, Path tempDir) {
        return filePart.content()
                .collectList()
                .flatMap(buffers -> Mono.fromCallable(() -> {
                    Files.createDirectories(tempDir);
                    int chunkIndex = 0;
                    long bytesInCurrentChunk = 0;
                    long CHUNK_SIZE = 1024 * 1024; // 1MB chunks
                    java.io.OutputStream out = Files.newOutputStream(tempDir.resolve("chunk-" + chunkIndex));
                    
                    try {
                        for (DataBuffer buffer : buffers) {
                            byte[] bytes = new byte[buffer.readableByteCount()];
                            buffer.read(bytes);
                            
                            int offset = 0;
                            while (offset < bytes.length) {
                                long remainingInChunk = CHUNK_SIZE - bytesInCurrentChunk;
                                int toWrite = (int) Math.min(bytes.length - offset, remainingInChunk);
                                
                                out.write(bytes, offset, toWrite);
                                offset += toWrite;
                                bytesInCurrentChunk += toWrite;
                                
                                if (bytesInCurrentChunk >= CHUNK_SIZE) {
                                    out.close();
                                    chunkIndex++;
                                    bytesInCurrentChunk = 0;
                                    out = Files.newOutputStream(tempDir.resolve("chunk-" + chunkIndex));
                                }
                            }
                        }
                    } finally {
                        out.close();
                    }
                    
                    Path lastChunk = tempDir.resolve("chunk-" + chunkIndex);
                    if (Files.exists(lastChunk) && Files.size(lastChunk) == 0) {
                        Files.delete(lastChunk);
                        return chunkIndex;
                    }
                    return chunkIndex + 1;
                }));
    }

    private Mono<Path> writeDirectFile(FilePart filePart, Path targetFile) {
        try {
            Files.createDirectories(targetFile.getParent());
        } catch (IOException e) {
            return Mono.error(e);
        }
        return filePart.transferTo(targetFile).thenReturn(targetFile);
    }

    private String detectMimeType(Path path) {
        return MediaTypeFactory.getMediaType(path.getFileName().toString())
                .map(org.springframework.http.MediaType::toString)
                .orElse("application/octet-stream");
    }

    private Mono<Void> checkRateLimit(ServerWebExchange exchange) {
        String ip = "unknown";
        if (exchange != null && exchange.getRequest().getRemoteAddress() != null) {
            ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        
        final String finalIp = ip;
        return Mono.fromRunnable(() -> {
            long now = System.currentTimeMillis();
            ipUploadTimestamps.compute(finalIp, (key, list) -> {
                if (list == null) {
                    list = new java.util.ArrayList<>();
                }
                list.removeIf(t -> now - t > 60000); // 1 minute window
                
                if (list.size() >= 5) {
                    throw new IllegalArgumentException("Terlalu banyak mengunggah berkas. Silakan coba lagi dalam 1 menit.");
                }
                
                list.add(now);
                return list;
            });
        }).then();
    }

    private Mono<String> resolveFolderName(String folderId, String folderType, Long userId, Long externalAccountId) {
        if ("LOCAL".equalsIgnoreCase(folderType)) {
            try {
                if (folderId == null) {
                    return Mono.just("Folder VPS");
                }
                UUID uuid = UUID.fromString(folderId);
                return folderRepository.findByIdAndUserId(uuid, userId)
                        .map(Folder::getName)
                        .defaultIfEmpty("Folder VPS")
                        .onErrorReturn("Folder VPS");
            } catch (Exception e) {
                log.warn("Invalid UUID or error for local folder share: {}", folderId);
                return Mono.just("Folder VPS");
            }
        } else {
            if (externalAccountId == null) {
                return Mono.just("Folder Google Drive (Tidak Terhubung)");
            }
            return googleDriveClient.getFileName(externalAccountId, folderId)
                    .onErrorReturn("Folder Google Drive (Tidak Terhubung)")
                    .defaultIfEmpty("Folder Google Drive");
        }
    }

    @Override
    public Mono<Boolean> isDescendant(UUID currentFolderId, UUID rootFolderId, Long userId) {
        if (currentFolderId.equals(rootFolderId)) {
            return Mono.just(true);
        }
        return folderRepository.findByIdAndUserId(currentFolderId, userId)
                .flatMap(folder -> {
                    if (folder.getParentId() == null) {
                        return Mono.just(false);
                    }
                    if (folder.getParentId().equals(rootFolderId)) {
                        return Mono.just(true);
                    }
                    return isDescendant(folder.getParentId(), rootFolderId, userId);
                })
                .defaultIfEmpty(false);
    }

    @Override
    @Transactional
    public Mono<FileResponse> getSharedFileMetadataPublic(String shareToken, String fileId) {
        return folderSharedRepository.findByShareToken(shareToken)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Link share tidak ditemukan.")))
                .flatMap(shared -> {
                    if (shared.getExpiresAt() != null && LocalDateTime.now(java.time.ZoneOffset.UTC).isAfter(shared.getExpiresAt())) {
                        return Mono.error(new IllegalArgumentException("Tautan berbagi folder telah kedaluwarsa."));
                    }

                    if ("LOCAL".equalsIgnoreCase(shared.getFolderType())) {
                        UUID fileUUID = UUID.fromString(fileId);
                        return fileRepository.findById(fileUUID)
                                .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan.")))
                                .flatMap(file -> {
                                    UUID rootUUID = UUID.fromString(shared.getFolderId());
                                    UUID fileFolderId = file.getFolderId();
                                    if (fileFolderId == null) {
                                        return Mono.error(new IllegalArgumentException("Berkas tidak berada di dalam folder bersama ini."));
                                    }
                                    return isDescendant(fileFolderId, rootUUID, shared.getUserId())
                                            .flatMap(isOk -> {
                                                if (!isOk) {
                                                    return Mono.error(new IllegalArgumentException("Berkas tidak berada di dalam folder bersama ini."));
                                                }
                                                return Mono.just(new FileResponse(
                                                        file.getId().toString(),
                                                        file.getOriginalFileName(),
                                                        file.getSize(),
                                                        file.getCreatedAt(),
                                                        file.getProvider(),
                                                        file.getExternalAccountId(),
                                                        null
                                                ));
                                            });
                                });
                    } else {
                        // Google Drive
                        return isGoogleDriveDescendant(fileId, shared.getFolderId(), shared.getExternalAccountId())
                                .flatMap(isOk -> {
                                    if (!isOk) {
                                        return Mono.error(new IllegalArgumentException("Berkas tidak berada di dalam folder bersama ini."));
                                    }
                                    return googleDriveClient.getFileMetadata(shared.getExternalAccountId(), fileId)
                                            .map(meta -> {
                                                String name = (String) meta.get("name");
                                                Long size = meta.get("size") != null ? Long.parseLong(meta.get("size").toString()) : 0L;
                                                Instant created = meta.get("createdTime") != null ? Instant.parse(meta.get("createdTime").toString()) : Instant.now();
                                                return new FileResponse(
                                                        fileId,
                                                        name,
                                                        size,
                                                        created,
                                                        "GOOGLE_DRIVE",
                                                        shared.getExternalAccountId(),
                                                        null
                                                );
                                            });
                                });
                    }
                });
    }

    private Mono<Boolean> isGoogleDriveDescendant(String currentFileId, String rootFolderId, Long externalAccountId) {
        if (currentFileId == null || rootFolderId == null) {
            return Mono.just(false);
        }
        if (currentFileId.equals(rootFolderId)) {
            return Mono.just(true);
        }
        return googleDriveClient.getFileMetadata(externalAccountId, currentFileId)
                .flatMap(metadata -> {
                    java.util.List<String> parents = (java.util.List<String>) metadata.get("parents");
                    if (parents == null || parents.isEmpty()) {
                        return Mono.just(false);
                    }
                    if (parents.contains(rootFolderId)) {
                        return Mono.just(true);
                    }
                    return Flux.fromIterable(parents)
                            .flatMap(parentId -> isGoogleDriveDescendant(parentId, rootFolderId, externalAccountId))
                            .filter(Boolean::booleanValue)
                            .next()
                            .defaultIfEmpty(false);
                })
                .onErrorReturn(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<Long> getSharedFolderOwnerId(String shareToken) {
        return folderSharedRepository.findByShareToken(shareToken)
                .map(FolderShared::getUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<byte[]> downloadFileFromSharedFolderPublic(String shareToken, String fileId, org.springframework.web.server.ServerWebExchange exchange) {
        return getSharedFileMetadataPublic(shareToken, fileId)
                .flatMapMany(file -> {
                    String activityType = "GOOGLE_DRIVE".equalsIgnoreCase(file.provider()) ? "DOWNLOAD_SHARED_PUBLIC_GD" : "DOWNLOAD_SHARED_PUBLIC";
                    String desc = "GOOGLE_DRIVE".equalsIgnoreCase(file.provider())
                            ? "Mengunduh berkas publik Google Drive dari folder bersama: " + file.originalFileName() + " dengan token: " + shareToken
                            : "Mengunduh berkas publik dari folder bersama: " + file.originalFileName() + " dengan token: " + shareToken;

                    Mono<?> logMono = userActivityService.log(null, activityType, desc, exchange);

                    Flux<byte[]> dataStream;
                    if ("GOOGLE_DRIVE".equalsIgnoreCase(file.provider())) {
                        dataStream = googleDriveClient.downloadFile(file.externalAccountId(), fileId);
                    } else {
                        dataStream = getSharedFolderOwnerId(shareToken)
                                .flatMapMany(ownerId -> downloadStorageService.downloadFile(ownerId, UUID.fromString(fileId))
                                        .map(chunk -> chunk.data()));
                    }
                    return logMono.thenMany(dataStream);
                });
    }
}
