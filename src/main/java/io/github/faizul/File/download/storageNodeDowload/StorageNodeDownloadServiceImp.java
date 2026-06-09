package io.github.faizul.File.download.storageNodeDowload;

import io.github.faizul.File.core.FileRepository;
import io.github.faizul.File.download.DownloadService;
import io.github.faizul.File.download.DownloadSession;
import io.github.faizul.File.download.DownloadSessionRepository;
import io.github.faizul.File.download.FileStatus;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.File.dtos.DownloadInitRequest;
import io.github.faizul.File.dtos.DownloadInitResponse;
import io.github.faizul.File.dtos.DownloadStatusResponse;
import io.github.faizul.File.share.FileSharedRepository;
import io.github.faizul.Storage.download.DownloadStorageService;
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
public class StorageNodeDownloadServiceImp implements DownloadService {

    private final FileRepository fileRepository;
    private final FileSharedRepository fileSharedRepository;
    private final DownloadSessionRepository downloadSessionRepository;
    private final DownloadStorageService downloadStorageService;
    private final CurrentUserContext currentUserContext;

    @Override
    public Mono<DownloadInitResponse> init(DownloadInitRequest request) {
        return currentUserContext.getUserId()
                .flatMap(userId -> fileRepository.findById(request.fileId())
                        .switchIfEmpty(Mono.error(new NoSuchElementException("File Not Found")))
                        .flatMap(file -> {
                            Mono<Boolean> accessCheck = file.getUserId().equals(userId) ? 
                                Mono.just(true) : fileSharedRepository.existsByFileIdAndUserId(file.getId(), userId);
                            
                            return accessCheck.flatMap(hasAccess -> {
                                if (!hasAccess) {
                                    return Mono.error(new AccessDeniedException("Access Denied"));
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
                .switchIfEmpty(Mono.error(new NoSuchElementException("File Not Found")))
                .flatMap(file -> {
                    Mono<Boolean> accessCheck = file.getUserId().equals(userId) ? 
                        Mono.just(true) : fileSharedRepository.existsByFileIdAndUserId(file.getId(), userId);
                    
                    return accessCheck.flatMap(hasAccess -> {
                        if (!hasAccess) {
                            return Mono.error(new AccessDeniedException("Access Denied"));
                        }
                        return downloadSessionRepository.findFirstByFileIdOrderByCreatedAtDesc(fileId)
                                .switchIfEmpty(Mono.error(new NoSuchElementException("Download Session Not Found")));
                    });
                });
    }

    @Override
    public Flux<byte[]> streamFile(UUID fileId) {
        return currentUserContext.getUserId()
                .flatMapMany(userId -> getValidSession(fileId, userId)
                        .flatMap(session -> {
                            session.setStatus(FileStatus.STREAMING);
                            return downloadSessionRepository.save(session);
                        })
                        .flatMapMany(savedSession -> downloadStorageService.downloadFile(userId, fileId)
                                .concatMap(chunk -> downloadSessionRepository.findById(savedSession.getId())
                                        .flatMap(s -> {
                                            if (s.getStatus() == FileStatus.CANCELED) {
                                                return Mono.error(new IllegalArgumentException("Download canceled by user"));
                                            }
                                            s.setBytesSent(s.getBytesSent() + chunk.data().length);
                                            return downloadSessionRepository.save(s);
                                        })
                                        .thenReturn(chunk.data())
                                )
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
                        )
                );
    }

    @Deprecated(
            forRemoval = true
    )
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
                    return downloadSessionRepository.save(session);
                })
                .then();
    }
}

