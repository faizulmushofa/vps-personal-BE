package io.github.faizul.UploadUnit.Implementation;

import io.github.faizul.File.UploadService.UploadService;
import io.github.faizul.Storage.Upload.StorageClient;
import io.github.faizul.UploadUnit.IOCleaningService;
import io.github.faizul.UploadUnit.UploadCoordinator;
import io.github.faizul.UploadUnit.UploadUnitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadCoordinatorImp implements UploadCoordinator {

    private final UploadUnitService uploadUnitService;
    private final UploadService uploadService; 
    private final StorageClient storageClient;
    private final IOCleaningService cleanupService;

    private static final int BATCH_SIZE = 5;

    @Override
    public Mono<Void> handleChunkUpload(UUID fileId, int chunkIndex, FilePart filePart) {
        
        return uploadUnitService.receiveUnit(fileId, chunkIndex, filePart)
                .then(uploadService.getStatus(fileId))
                .flatMap(sessionResponse -> {
                    
                    int totalChunks = sessionResponse.totalChunks();
                    
                    // 1. Cek Komplesi 100% (Secara Atomik)
                    return uploadUnitService.isComplete(fileId, totalChunks)
                            .flatMap(isComplete -> {
                                if (isComplete) {
                                    return uploadUnitService.claimCompletion(fileId)
                                            .flatMap(claimed -> {
                                                if (claimed) {
                                                    return handleCompletion(fileId, totalChunks);
                                                }
                                                return Mono.empty();
                                            });
                                }
                                
                                // 2. Evaluasi Batch Saat Ini (Windowing Out-of-Order Proof)
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
                                                                        .flatMap(received -> handleBatchTrigger(fileId, batchStart, batchEnd, received));
                                                            }
                                                            return Mono.empty();
                                                        });
                                            }
                                            return Mono.empty();
                                        });
                            });
                });
    }

    private Mono<Void> handleBatchTrigger(UUID fileId, int batchStart, int batchEnd, int currentReceived) {
        log.info("[BATCH TRIGGER] Initiating batch {}-{} for file {}", batchStart, batchEnd, fileId);
        
        return storageClient.sendBatch(fileId, batchStart, batchEnd)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .then(cleanupService.cleanupBatch(fileId, batchStart, batchEnd))
                .then(uploadService.updateUploadProgress(fileId, currentReceived))
                .onErrorResume(e -> {
                    log.error("[BATCH ERROR] Failed to process batch {}-{} for file {}: {}", batchStart, batchEnd, fileId, e.getMessage());
                    return Mono.error(e);
                });
    }

    private Mono<Void> handleCompletion(UUID fileId, int totalChunks) {
        log.info("[COMPLETION DETECTED] All chunks received for file {}", fileId);
        
        return flushUnsentBatches(fileId, totalChunks)
                .then(storageClient.sendFinalSignal(fileId, totalChunks))
                .retryWhen(Retry.backoff(3,Duration.ofSeconds(1)))
                .then(uploadService.updateUploadProgress(fileId, totalChunks))
                .then(uploadService.markAsCompleted(fileId))
                .then(cleanupService.cleanupTempFiles(fileId))
                .then(uploadUnitService.cleanupMemory(fileId)) 
                .onErrorResume(e -> {
                    log.error("[COMPLETION ERROR] Failed to finalize file {}: {}", fileId, e.getMessage());
                    return Mono.error(e);
                });
    }

    private Mono<Void> flushUnsentBatches(UUID fileId, int totalChunks) {
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

                                return storageClient.sendBatch(fileId, batchStart, batchEnd)
                                        .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                                        .then(cleanupService.cleanupBatch(fileId, batchStart, batchEnd));
                            });
                })
                .then();
    }
}
