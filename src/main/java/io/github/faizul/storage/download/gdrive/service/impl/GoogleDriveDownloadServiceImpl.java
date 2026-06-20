package io.github.faizul.storage.download.gdrive.service.impl;

import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.storage.file.repository.FileRepository;
import io.github.faizul.storage.file.service.client.GoogleDriveClient;
import io.github.faizul.storage.download.gdrive.service.GoogleDriveDownloadService;
import io.github.faizul.storage.download.model.DownloadSession;
import io.github.faizul.storage.download.repository.DownloadSessionRepository;
import io.github.faizul.storage.download.model.FileStatus;
import io.github.faizul.storage.file.dtos.DownloadInitRequest;
import io.github.faizul.storage.file.dtos.DownloadInitResponse;
import io.github.faizul.storage.file.dtos.DownloadStatusResponse;
import io.github.faizul.storage.file.dtos.FileResponse;
import io.github.faizul.storage.share.repository.FileSharedRepository;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.user.repository.ExternalAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import io.github.faizul.storage.file.model.File;

@Service("googleDriveDownloadService")
@Transactional
@RequiredArgsConstructor
public class GoogleDriveDownloadServiceImpl implements GoogleDriveDownloadService {

    private static final java.util.Set<UUID> canceledSessions = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final FileRepository fileRepository;
    private final FileSharedRepository fileSharedRepository;
    private final DownloadSessionRepository downloadSessionRepository;
    private final CurrentUserContext currentUserContext;
    private final GoogleDriveClient googleDriveClient;
    private final UserActivityService userActivityService;
    private final ExternalAccountRepository externalAccountRepository;

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
                                    return Mono.error(new AccessDeniedException("Anda tidak memiliki akses untuk mengunduh berkas Google Drive ini"));
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
                            return Mono.error(new AccessDeniedException("Anda tidak memiliki akses untuk mengunduh berkas Google Drive ini"));
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
                                .flatMap(this::updateSessionToStreaming)
                                .flatMapMany(savedSession -> {
                                    Flux<byte[]> dataStream = googleDriveClient.downloadFile(file.getExternalAccountId(), file.getStorageName());
                                    return processDataStream(dataStream, savedSession.getId());
                                })
                        )
                );
    }

    private Mono<DownloadSession> updateSessionToStreaming(DownloadSession session) {
        session.setStatus(FileStatus.STREAMING);
        return downloadSessionRepository.save(session);
    }

    private Flux<byte[]> processDataStream(Flux<byte[]> dataStream, UUID sessionId) {
        return dataStream
                .map(chunk -> {
                    if (canceledSessions.contains(sessionId)) {
                        throw new IllegalArgumentException("Download canceled by user");
                    }
                    return chunk;
                })
                .doOnComplete(() -> finalizeSessionStatus(sessionId, FileStatus.COMPLETED, false))
                .doOnError(err -> finalizeSessionStatus(sessionId, FileStatus.FAILED, true))
                .doFinally(signalType -> canceledSessions.remove(sessionId));
    }

    private void finalizeSessionStatus(UUID sessionId, FileStatus targetStatus, boolean isError) {
        downloadSessionRepository.findById(sessionId)
                .flatMap(s -> {
                    if (s.getStatus() != FileStatus.CANCELED && (isError || s.getStatus() != FileStatus.FAILED)) {
                        s.setStatus(targetStatus);
                        if (targetStatus == FileStatus.COMPLETED) {
                            s.setCompletedAt(Instant.now());
                        }
                    }
                    return downloadSessionRepository.save(s);
                })
                .subscribe();
    }

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
    public Mono<UUID> resolveFileId(String fileIdString, Long userId) {
        try {
            return Mono.just(UUID.fromString(fileIdString));
        } catch (IllegalArgumentException e) {
            return fileRepository.findByStorageNameAndProvider(fileIdString, "GOOGLE_DRIVE")
                    .map(File::getId)
                    .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan!")));
        }
    }

    @Override
    public Mono<Map<String, Object>> getExternalFileMetadata(Long externalAccountId, String fileId, Long userId) {
        return externalAccountRepository.findByIdAndUserId(externalAccountId, userId)
                .switchIfEmpty(Mono.error(new SecurityException("Akses ditolak: Akun eksternal tidak valid")))
                .flatMap(account -> googleDriveClient.getFileMetadata(externalAccountId, fileId));
    }

    @Override
    public Flux<byte[]> downloadExternalFile(Long externalAccountId, String fileId, Long userId, org.springframework.web.server.ServerWebExchange exchange) {
        return externalAccountRepository.findByIdAndUserId(externalAccountId, userId)
                .switchIfEmpty(Mono.error(new SecurityException("Akses ditolak: Akun eksternal tidak valid")))
                .flatMapMany(account -> googleDriveClient.getFileMetadata(externalAccountId, fileId)
                        .flatMapMany(metadata -> {
                            String name = (String) metadata.get("name");
                            return userActivityService.log(userId, "DOWNLOAD_GD", "Mengunduh berkas Google Drive: " + name, exchange)
                                    .thenMany(googleDriveClient.downloadFile(externalAccountId, fileId));
                        }));
    }

    @Override
    public Flux<byte[]> downloadFileWithLog(UUID fileId, Long userId, org.springframework.web.server.ServerWebExchange exchange) {
        return fileRepository.findById(fileId)
                .flatMapMany(file -> userActivityService.log(userId, "DOWNLOAD_GD", "Mengunduh berkas Google Drive: " + file.getOriginalFileName(), exchange)
                        .thenMany(streamFile(fileId)));
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
}
