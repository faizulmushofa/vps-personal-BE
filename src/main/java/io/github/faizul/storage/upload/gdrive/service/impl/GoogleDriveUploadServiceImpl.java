package io.github.faizul.storage.upload.gdrive.service.impl;

import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.storage.file.model.File;
import io.github.faizul.storage.file.repository.FileRepository;
import io.github.faizul.storage.file.service.client.GoogleDriveClient;
import io.github.faizul.storage.file.dtos.InitRequest;
import io.github.faizul.storage.file.dtos.InitResponse;
import io.github.faizul.storage.file.dtos.UploadSessionResponse;
import io.github.faizul.storage.upload.model.FileStatus;
import io.github.faizul.storage.upload.gdrive.service.GoogleDriveUploadService;
import io.github.faizul.storage.upload.model.UploadSession;
import io.github.faizul.storage.upload.repository.UploadSessionRepository;
import io.github.faizul.storage.uploadunit.model.Chunk;
import io.github.faizul.storage.uploadunit.service.IOCleaningService;
import io.github.faizul.storage.uploadunit.service.UploadUnitService;
import io.github.faizul.infra.config.StorageConfig;
import io.github.faizul.security.filter.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service("googleDriveUploadService")
@Transactional
@RequiredArgsConstructor
@Slf4j
public class GoogleDriveUploadServiceImpl implements GoogleDriveUploadService {

    private final FileRepository fileRepository;
    private final UploadSessionRepository uploadSessionRepository;
    private final CurrentUserContext currentUserContext;
    private final StorageConfig storageConfig;
    private final GoogleDriveClient googleDriveClient;
    private final UploadUnitService uploadUnitService;
    private final IOCleaningService cleanupService;
    private final UserActivityService userActivityService;

    @Override
    public Mono<InitResponse> create(InitRequest request) {
        UUID fileId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        String extension = "";
        int lastDot = request.fileName().lastIndexOf(".");
        if (lastDot != -1) {
            extension = request.fileName().substring(lastDot);
        }
        String storageName = UUID.randomUUID() + extension;

        return currentUserContext.getUserId()
                .flatMap(userId -> {
                    String tempPath = storageConfig.tempDir(userId, fileId).toString();
                    try {
                        java.nio.file.Files.createDirectories(Paths.get(tempPath));
                        if (request.folderId() != null && !request.folderId().isBlank()) {
                            java.nio.file.Files.writeString(Paths.get(tempPath, "parentFolderId.txt"), request.folderId());
                        }
                    } catch (java.io.IOException e) {
                        return Mono.error(e);
                    }

                    File file = File.builder()
                            .id(fileId)
                            .userId(userId)
                            .originalFileName(request.fileName())
                            .storageName(storageName)
                            .size(request.totalSize())
                            .provider("GOOGLE_DRIVE")
                            .externalAccountId(request.externalAccountId())
                            .build();

                    UploadSession session = UploadSession.builder()
                            .id(sessionId)
                            .fileId(fileId)
                            .tempPath(tempPath)
                            .totalChunks(Chunk.calculateTotalChunks(request.totalSize()))
                            .uploadedChunks(0)
                            .status(FileStatus.UPLOADING)
                            .build();

                    return fileRepository.save(file)
                            .then(uploadSessionRepository.save(session))
                            .then(userActivityService.log(userId, "UPLOAD_INIT_GD", "Mengunggah berkas ke Google Drive: " + request.fileName(), null))
                            .thenReturn(file);
                })
                .map(file -> new InitResponse(file.getId(), file.getOriginalFileName()));
    }

    private Mono<UploadSession> getValidSession(UUID fileId, Long userId) {
        return fileRepository.findById(fileId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan")))
                .flatMap(file -> {
                    if (!file.getUserId().equals(userId)) {
                        return Mono.error(new org.springframework.security.access.AccessDeniedException("Anda tidak memiliki akses untuk mengunggah berkas Google Drive ini"));
                    }
                    return uploadSessionRepository.findByFileId(fileId)
                            .switchIfEmpty(Mono.error(new NoSuchElementException("Sesi unggah tidak ditemukan")));
                });
    }

    @Override
    public Mono<Void> updateUploadProgress(UUID fileId, int receivedChunks) {
        return currentUserContext.getUserId()
                .flatMap(userId -> getValidSession(fileId, userId))
                .flatMap(session -> {
                    session.setUploadedChunks(receivedChunks);
                    if (session.getStatus() != FileStatus.UPLOADING) {
                        session.setStatus(FileStatus.UPLOADING);
                    }
                    return uploadSessionRepository.save(session);
                })
                .then();
    }

    @Override
    public Mono<Void> markAsCompleted(UUID fileId) {
        return currentUserContext.getUserId()
                .flatMap(userId -> getValidSession(fileId, userId))
                .flatMap(session -> {
                    session.setStatus(FileStatus.COMPLETED);
                    session.setUploadedChunks(session.getTotalChunks());
                    session.setCompletedAt(Instant.now());
                    return uploadSessionRepository.save(session);
                })
                .then();
    }

    @Override
    public Mono<Void> cancelUpload(UUID fileId) {
        return currentUserContext.getUserId()
                .flatMap(userId -> getValidSession(fileId, userId)
                        .flatMap(session -> {
                            session.setStatus(FileStatus.CANCELED);
                            return uploadSessionRepository.save(session)
                                    .flatMap(savedSession -> Mono.<Void>fromRunnable(() -> {
                                        try {
                                            FileSystemUtils.deleteRecursively(Paths.get(savedSession.getTempPath()));
                                        } catch (Exception e) {
                                            // ignore
                                        }
                                    })
                                    .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                                    .then(userActivityService.log(userId, "UPLOAD_CANCEL_GD", "Membatalkan unggah berkas Google Drive ID: " + fileId, null))
                                    .thenReturn(savedSession));
                        })
                )
                .then();
    }

    @Override
    public Mono<UploadSessionResponse> getStatus(UUID fileId) {
        return currentUserContext.getUserId()
                .flatMap(userId -> getValidSession(fileId, userId))
                .map(session -> new UploadSessionResponse(
                        session.getId(),
                        session.getFileId(),
                        session.getUploadedChunks(),
                        session.getTotalChunks(),
                        session.getStatus(),
                        session.getCreatedAt(),
                        session.getCompletedAt()));
    }

    @Override
    public Mono<Void> handleChunkUpload(UUID fileId, int chunkIndex, FilePart filePart) {
        return currentUserContext.getUserId()
                .flatMap(userId -> uploadUnitService.receiveUnit(fileId, chunkIndex, filePart)
                        .then(getStatus(fileId))
                        .flatMap(sessionResponse -> checkAndTriggerCompletion(userId, fileId, sessionResponse.totalChunks()))
                );
    }

    private Mono<Void> checkAndTriggerCompletion(Long userId, UUID fileId, int totalChunks) {
        return uploadUnitService.isComplete(fileId, totalChunks)
                .flatMap(isComplete -> {
                    if (isComplete) {
                        return uploadUnitService.claimCompletion(fileId)
                                .flatMap(claimed -> {
                                    if (claimed) {
                                        return handleCompletion(userId, fileId, totalChunks);
                                    }
                                    return Mono.empty();
                                });
                    }
                    return uploadUnitService.getReceivedUnit(fileId)
                            .flatMap(received -> updateUploadProgress(fileId, received));
                });
    }

    private Mono<Void> handleCompletion(Long userId, UUID fileId, int totalChunks) {
        log.info("[GDrive Upload Completion] All chunks received for file {}", fileId);
        return fileRepository.findById(fileId)
                .flatMap(file -> combineChunks(userId, fileId, totalChunks)
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                        .flatMap(combinedFilePath -> uploadToGoogleDriveAndSave(userId, file, combinedFilePath))
                        .then(updateUploadProgress(fileId, totalChunks))
                        .then(markAsCompleted(fileId))
                        .then(cleanupService.cleanupTempFiles(userId, fileId))
                        .then(uploadUnitService.cleanupMemory(fileId))
                );
    }

    private Mono<Void> uploadToGoogleDriveAndSave(Long userId, File file, Path combinedFilePath) {
        String mimeType = detectMimeType(combinedFilePath);
        
        String parentFolderId = null;
        Path parentFile = storageConfig.tempDir(userId, file.getId()).resolve("parentFolderId.txt");
        if (java.nio.file.Files.exists(parentFile)) {
            try {
                parentFolderId = java.nio.file.Files.readString(parentFile).trim();
            } catch (Exception e) {
                log.warn("Failed to read parentFolderId.txt: {}", e.getMessage());
            }
        }
        
        final String finalParent = parentFolderId;
        return googleDriveClient.uploadFile(file.getExternalAccountId(), combinedFilePath, file.getOriginalFileName(), mimeType, finalParent)
                .flatMap(googleFileId -> {
                    file.setStorageName(googleFileId);
                    return fileRepository.save(file);
                })
                .then(Mono.fromRunnable(() -> deleteFileIfExists(combinedFilePath)));
    }

    private String detectMimeType(Path path) {
        try {
            String contentType = java.nio.file.Files.probeContentType(path);
            if (contentType != null) {
                return contentType;
            }
        } catch (Exception e) {
            // ignore
        }
        return "application/octet-stream";
    }

    private void deleteFileIfExists(Path path) {
        try {
            java.nio.file.Files.deleteIfExists(path);
        } catch (Exception e) {
            log.error("Failed to delete temp combined file: {}", e.getMessage());
        }
    }

    private Mono<Path> combineChunks(Long userId, UUID fileId, int totalChunks) {
        return Mono.fromCallable(() -> {
            Path tempDir = storageConfig.tempDir(userId, fileId);
            Path combinedFile = tempDir.resolve("combined-" + fileId + ".tmp");
            try (java.io.OutputStream out = new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(combinedFile))) {
                for (int i = 0; i < totalChunks; i++) {
                    Path chunkFile = tempDir.resolve("chunk-" + i);
                    if (!java.nio.file.Files.exists(chunkFile)) {
                        throw new java.io.FileNotFoundException("Chunk file " + i + " not found!");
                    }
                    java.nio.file.Files.copy(chunkFile, out);
                }
            }
            return combinedFile;
        });
    }
}
