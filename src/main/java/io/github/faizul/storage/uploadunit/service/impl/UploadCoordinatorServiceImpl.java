package io.github.faizul.storage.uploadunit.service.impl;

import io.github.faizul.storage.upload.service.UploadService;
import io.github.faizul.storage.upload.service.UploadStorageService;
import io.github.faizul.storage.uploadunit.service.IOCleaningService;
import io.github.faizul.storage.uploadunit.service.UploadCoordinator;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.storage.uploadunit.service.UploadUnitService;
import io.github.faizul.infra.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class UploadCoordinatorServiceImpl implements UploadCoordinator {

    private final UploadUnitService uploadUnitService;
    private final UploadService uploadService; 
    private final UploadStorageService uploadStorageClient;
    private final IOCleaningService cleanupService;
    private final CurrentUserContext currentUserContext;
    private final StorageConfig storageConfig;

    public UploadCoordinatorServiceImpl(
            UploadUnitService uploadUnitService,
            @Qualifier("storageNodeUploadService") UploadService uploadService,
            UploadStorageService uploadStorageClient,
            IOCleaningService cleanupService,
            CurrentUserContext currentUserContext,
            StorageConfig storageConfig) {
        this.uploadUnitService = uploadUnitService;
        this.uploadService = uploadService;
        this.uploadStorageClient = uploadStorageClient;
        this.cleanupService = cleanupService;
        this.currentUserContext = currentUserContext;
        this.storageConfig = storageConfig;
    }

    private static final int BATCH_SIZE = 5;

    @Override
    public Mono<Void> handleChunkUpload(UUID fileId, int chunkIndex, FilePart filePart) {
        
        return currentUserContext.getUserId()
            .flatMap(userId -> uploadUnitService.receiveUnit(fileId, chunkIndex, filePart)
                .then(uploadService.getStatus(fileId))
                .flatMap(sessionResponse -> {
                    
                    int totalChunks = sessionResponse.totalChunks();

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

                                int batchIndex = chunkIndex / BATCH_SIZE;
                                int batchStart = batchIndex * BATCH_SIZE;
                                int batchEnd = Math.min(batchStart + BATCH_SIZE - 1, totalChunks - 1);

                                return uploadUnitService.isBatchReady(fileId, batchStart, batchEnd)
                                        .flatMap(isReady -> {
                                            if (isReady) {
                                                return uploadUnitService.claimBatch(fileId, batchIndex)
                                                        .flatMap(claimed -> {
                                                            if (claimed) {
                                                                return uploadUnitService.getReceivedUnit(fileId)
                                                                        .flatMap(received -> handleBatchTrigger(userId, fileId, batchStart, batchEnd, received));
                                                            }
                                                            return Mono.empty();
                                                        });
                                            }
                                            return uploadUnitService.getReceivedUnit(fileId)
                                                    .flatMap(received -> uploadService.updateUploadProgress(fileId, received));
                                        });
                            });
                }));
    }

    private Mono<Void> handleBatchTrigger(Long userId, UUID fileId, int batchStart, int batchEnd, int currentReceived) {
        log.info("[BATCH TRIGGER] Initiating batch {}-{} for file {}", batchStart, batchEnd, fileId);
        
        return uploadStorageClient.sendBatch(userId, fileId, batchStart, batchEnd)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .then(cleanupService.cleanupBatch(userId, fileId, batchStart, batchEnd))
                .then(uploadService.updateUploadProgress(fileId, currentReceived))
                .onErrorResume(e -> {
                    log.error("[BATCH ERROR] Failed to process batch {}-{} for file {}: {}", batchStart, batchEnd, fileId, e.getMessage());
                    return Mono.error(e);
                });
    }

    private Mono<Void> handleCompletion(Long userId, UUID fileId, int totalChunks) {
        log.info("[COMPLETION DETECTED] All chunks received for file {}", fileId);

        return flushUnsentBatches(userId, fileId, totalChunks)
                .then(uploadStorageClient.sendFinalSignal(userId, fileId, totalChunks))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .then(uploadService.updateUploadProgress(fileId, totalChunks))
                .then(uploadService.markAsCompleted(fileId))
                .then(cleanupService.cleanupTempFiles(userId, fileId))
                .then(uploadUnitService.cleanupMemory(fileId))
                .onErrorResume(e -> {
                    log.error("[COMPLETION ERROR] Failed to finalize file {}: {}", fileId, e.getMessage());
                    return Mono.error(e);
                });
    }

    private Mono<Void> flushUnsentBatches(Long userId, UUID fileId, int totalChunks) {
        int totalBatches = (int) Math.ceil((double) totalChunks / BATCH_SIZE);

        return Flux.range(0, totalBatches)
                .concatMap(batchIndex -> {
                    int batchStart = batchIndex * BATCH_SIZE;
                    int batchEnd = Math.min(batchStart + BATCH_SIZE - 1, totalChunks - 1);

                    return uploadUnitService.claimBatch(fileId, batchIndex)
                            .flatMap(claimed -> {
                                if (!claimed) {
                                    return Mono.empty();
                                }

                                log.info("[COMPLETION FLUSH] Sending unsent batch {}-{} for file {}", batchStart, batchEnd, fileId);

                                return uploadStorageClient.sendBatch(userId, fileId, batchStart, batchEnd)
                                        .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                                        .then(cleanupService.cleanupBatch(userId, fileId, batchStart, batchEnd));
                            });
                })
                .then();
    }
}
