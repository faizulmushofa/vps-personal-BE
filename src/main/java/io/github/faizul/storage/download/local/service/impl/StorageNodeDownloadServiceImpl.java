package io.github.faizul.storage.download.local.service.impl;

import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.storage.file.repository.FileRepository;
import io.github.faizul.storage.download.local.service.StorageNodeDownloadService;
import io.github.faizul.storage.download.model.DownloadSession;
import io.github.faizul.storage.download.repository.DownloadSessionRepository;
import io.github.faizul.storage.download.model.FileStatus;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.storage.file.dtos.DownloadInitRequest;
import io.github.faizul.storage.file.dtos.DownloadInitResponse;
import io.github.faizul.storage.file.dtos.DownloadStatusResponse;
import io.github.faizul.storage.file.dtos.FileResponse;
import io.github.faizul.storage.share.repository.FileSharedRepository;
import io.github.faizul.storage.download.service.DownloadStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.NoSuchElementException;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service("storageNodeDownloadService")
@Transactional
@RequiredArgsConstructor
public class StorageNodeDownloadServiceImpl implements StorageNodeDownloadService {

    private static final java.util.Set<UUID> canceledSessions = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final FileRepository fileRepository;
    private final FileSharedRepository fileSharedRepository;
    private final DownloadSessionRepository downloadSessionRepository;
    private final DownloadStorageService downloadStorageService;
    private final CurrentUserContext currentUserContext;
    private final UserActivityService userActivityService;

    @Override
    public Mono<DownloadInitResponse> init(DownloadInitRequest request) {
        return currentUserContext.getUserId()
                .flatMap(userId -> fileRepository.findById(request.fileId())
                        .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan")))
                        .flatMap(file -> {
                            Mono<Boolean> accessCheck = file.getUserId().equals(userId) ? 
                                Mono.just(true) : fileSharedRepository.findByFileIdAndUserId(file.getId(), userId)
                                    .map(shared -> shared.getExpiresAt() == null || LocalDateTime.now(java.time.ZoneOffset.UTC).isBefore(shared.getExpiresAt()))
                                    .defaultIfEmpty(false);
                            
                            return accessCheck.flatMap(hasAccess -> {
                                if (!hasAccess) {
                                    return Mono.error(new AccessDeniedException("Anda tidak memiliki akses untuk mengunduh berkas ini"));
                                }

                                UUID sessionId = UUID.randomUUID();
                                DownloadSession session = DownloadSession.builder()
                                        .id(sessionId)
                                        .fileId(file.getId())
                                        .userId(userId)
                                        .status(FileStatus.INIT)
                                        .totalBytes(file.getSize())
                                        .bytesSent(0L)
                                        .startedAt(Instant.now())
                                        .build();

                                return downloadSessionRepository.save(session)
                                        .map(savedSession -> new DownloadInitResponse(
                                                savedSession.getId(),
                                                file.getId(),
                                                file.getOriginalFileName(),
                                                file.getSize(),
                                                savedSession.getStatus()
                                        ));
                            });
                        }));
    }

    private Mono<DownloadSession> getValidSession(UUID fileId, Long userId) {
        return fileRepository.findById(fileId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan")))
                .flatMap(file -> {
                    Mono<Boolean> accessCheck = file.getUserId().equals(userId) ? 
                        Mono.just(true) : fileSharedRepository.findByFileIdAndUserId(file.getId(), userId)
                            .map(shared -> shared.getExpiresAt() == null || LocalDateTime.now(java.time.ZoneOffset.UTC).isBefore(shared.getExpiresAt()))
                            .defaultIfEmpty(false);
                    
                    return accessCheck.flatMap(hasAccess -> {
                        if (!hasAccess) {
                            return Mono.error(new AccessDeniedException("Anda tidak memiliki akses untuk mengunduh berkas ini"));
                        }
                        return downloadSessionRepository.findFirstByFileIdOrderByCreatedAtDesc(fileId)
                                .filter(session -> session.getStatus() == FileStatus.INIT || session.getStatus() == FileStatus.STREAMING)
                                .switchIfEmpty(Mono.defer(() -> {
                                    UUID sessionId = UUID.randomUUID();
                                    DownloadSession session = DownloadSession.builder()
                                            .id(sessionId)
                                            .fileId(file.getId())
                                            .userId(userId)
                                            .status(FileStatus.INIT)
                                            .totalBytes(file.getSize())
                                            .bytesSent(0L)
                                            .startedAt(Instant.now())
                                            .build();
                                    return downloadSessionRepository.save(session);
                                }));
                    });
                });
    }

    @Override
    public Flux<byte[]> streamFile(UUID fileId) {
        return currentUserContext.getUserId()
                .flatMapMany(userId -> fileRepository.findById(fileId)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("File Not Found")))
                        .flatMapMany(file -> getValidSession(fileId, userId)
                                .flatMap(session -> {
                                    session.setStatus(FileStatus.STREAMING);
                                    return downloadSessionRepository.save(session);
                                })
                                .flatMapMany(savedSession -> {
                                    Flux<byte[]> dataStream = downloadStorageService.downloadFile(file.getUserId(), fileId)
                                            .map(chunk -> chunk.data());

                                    return dataStream
                                            .map(chunk -> {
                                                if (canceledSessions.contains(savedSession.getId())) {
                                                    throw new IllegalArgumentException("Download canceled by user");
                                                }
                                                return chunk;
                                            })
                                            .doOnComplete(() -> downloadSessionRepository.findById(savedSession.getId())
                                                    .flatMap(s -> {
                                                        if (s.getStatus() != FileStatus.CANCELED && s.getStatus() != FileStatus.FAILED) {
                                                            s.setStatus(FileStatus.COMPLETED);
                                                            s.setCompletedAt(Instant.now());
                                                        }
                                                        return downloadSessionRepository.save(s);
                                                    }).subscribe()
                                            )
                                            .doOnError(err -> downloadSessionRepository.findById(savedSession.getId())
                                                    .flatMap(s -> {
                                                        if (s.getStatus() != FileStatus.CANCELED) {
                                                            s.setStatus(FileStatus.FAILED);
                                                        }
                                                        return downloadSessionRepository.save(s);
                                                    }).subscribe()
                                            )
                                            .doFinally(signalType -> canceledSessions.remove(savedSession.getId()));
                                })
                        )
                );
    }

    @Deprecated(forRemoval = true)
    @Override
    public Flux<byte[]> streamFileChunked(UUID fileId, int chunkSize) {
        return streamFile(fileId)
                .concatMap(bytes -> Flux.range(0, (bytes.length + chunkSize - 1) / chunkSize)
                        .map(i -> {
                            int start = i * chunkSize;
                            int end = Math.min(start + chunkSize, bytes.length);
                            byte[] slice = new byte[end - start];
                            System.arraycopy(bytes, start, slice, 0, slice.length);
                            return slice;
                        })
                );
    }

    @Override
    public Mono<DownloadStatusResponse> getStatus(UUID fileId) {
        return currentUserContext.getUserId()
                .flatMap(userId -> getValidSession(fileId, userId))
                .map(session -> {
                    Double progress = 0.0;
                    if (session.getTotalBytes() > 0) {
                        progress = (double) session.getBytesSent() / session.getTotalBytes();
                    }
                    return new DownloadStatusResponse(
                            session.getId(),
                            session.getFileId(),
                            session.getStatus(),
                            session.getTotalBytes(),
                            session.getBytesSent(),
                            progress
                    );
                });
    }

    @Override
    public Mono<Void> cancel(UUID fileId) {
        return currentUserContext.getUserId()
                .flatMap(userId -> getValidSession(fileId, userId))
                .flatMap(session -> {
                    session.setStatus(FileStatus.CANCELED);
                    canceledSessions.add(session.getId());
                    return downloadSessionRepository.save(session);
                })
                .then();
    }

    @Override
    public Mono<FileResponse> getFileDetails(UUID fileId) {
        return fileRepository.findById(fileId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan!")))
                .map(file -> new FileResponse(
                        file.getId(),
                        file.getOriginalFileName(),
                        file.getSize(),
                        file.getCreatedAt(),
                        file.getProvider(),
                        file.getExternalAccountId(),
                        null
                ));
    }

    @Override
    public Flux<byte[]> downloadFileWithLog(UUID fileId, Long userId, org.springframework.web.server.ServerWebExchange exchange) {
        return fileRepository.findById(fileId)
                .flatMapMany(file -> userActivityService.log(userId, "DOWNLOAD", "Mengunduh berkas: " + file.getOriginalFileName(), exchange)
                        .thenMany(streamFile(fileId)));
    }
}
