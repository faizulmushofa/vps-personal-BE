package io.github.faizul.File.upload.StorageNodeUplad;

import io.github.faizul.File.core.File;
import io.github.faizul.File.core.FileRepository;
import io.github.faizul.File.upload.FileStatus;
import io.github.faizul.File.upload.UploadService;
import io.github.faizul.File.upload.UploadSession;
import io.github.faizul.File.upload.UploadSessionRepository;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.File.dtos.UploadSessionResponse;
import io.github.faizul.File.dtos.InitRequest;
import io.github.faizul.File.dtos.InitResponse;
import io.github.faizul.infra.config.StorageConfig;
import io.github.faizul.UploadUnit.Helper.Chunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;
import java.util.NoSuchElementException;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

import io.github.faizul.User.core.UserRepository;

@Service("storageNodeUploadService")
@Transactional
@RequiredArgsConstructor
public class UploadNodeServiceImp implements UploadService {

    private final FileRepository fileRepository;
    private final UploadSessionRepository uploadSessionRepository;
    private final CurrentUserContext currentUserContext;
    private final Scheduler fileCleanupScheduler;
    private final StorageConfig storageConfig;
    private final UserRepository userRepository;

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
                .flatMap(userId -> userRepository.findById(userId)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("User Not Found")))
                        .flatMap(user -> {
                            Mono<Void> quotaCheck = fileRepository.calculateUsedStorageByUserId(userId)
                                    .flatMap(usedStorage -> {
                                        long totalSize = request.totalSize();
                                        long quota = user.getStorageQuota() != null ? user.getStorageQuota() : 1073741824L;
                                        if (usedStorage + totalSize > quota) {
                                            return Mono.error(new IllegalArgumentException(
                                                    "Kapasitas penyimpanan tidak mencukupi untuk file ini!"));
                                        }
                                        return Mono.empty();
                                    });

                            return quotaCheck.then(Mono.defer(() -> {
                                String tempPath = storageConfig.tempDir(userId, fileId).toString();
                                File file = File.builder()
                                        .id(fileId)
                                        .userId(userId)
                                        .originalFileName(request.fileName())
                                        .storageName(storageName)
                                        .size(request.totalSize())
                                        .provider("STORAGE_NODE")
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
                                        .thenReturn(file);
                            }));
                        }))
                .map(file -> new InitResponse(file.getId(), file.getOriginalFileName()));
    }

    private Mono<UploadSession> getValidSession(UUID fileId, Long userId) {
        return fileRepository.findById(fileId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan")))
                .flatMap(file -> {
                    if (!file.getUserId().equals(userId)) {
                        return Mono.error(new AccessDeniedException("Anda tidak memiliki akses untuk mengunggah berkas ini"));
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
                .flatMap(userId -> getValidSession(fileId, userId))
                .flatMap(session -> {
                    session.setStatus(FileStatus.CANCELED);

                    return uploadSessionRepository.save(session)
                            .flatMap(savedSession -> Mono.<Void>fromRunnable(() -> {
                                try {
                                    FileSystemUtils.deleteRecursively(Paths.get(savedSession.getTempPath()));
                                } catch (Exception e) {
                                    // silently ignore temp-dir cleanup failures during cancel
                                }
                            })
                                    .subscribeOn(fileCleanupScheduler)
                                    .thenReturn(savedSession));
                })
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
}
