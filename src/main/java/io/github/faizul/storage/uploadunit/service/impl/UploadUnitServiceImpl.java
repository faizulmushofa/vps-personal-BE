package io.github.faizul.storage.uploadunit.service.impl;

import io.github.faizul.storage.uploadunit.service.UploadUnitService;
import io.github.faizul.storage.uploadunit.tracker.UploadUnitTracker;
import io.github.faizul.storage.uploadunit.service.UploadUnitWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UploadUnitServiceImpl implements UploadUnitService {

    private final UploadUnitWriter uploadUnitWriter;
    private final UploadUnitTracker inMemoryTracker;

    @Override
    public Mono<Void> receiveUnit(UUID uuid, int index, FilePart filePart) {
        return uploadUnitWriter.write(uuid, index, filePart)
                .doOnSuccess(v -> inMemoryTracker.markReceived(uuid, index));
    }

    @Override
    public Mono<Boolean> isUnitReceived(UUID fileId, int index) {
        return Mono.just(inMemoryTracker.isReceived(fileId, index));
    }

    @Override
    public Mono<Integer> getReceivedUnit(UUID fileId) {
        return Mono.just(inMemoryTracker.countReceived(fileId));
    }

    @Override
    public Mono<Boolean> isComplete(UUID fileId, int totalChunks) {
        return Mono.just(inMemoryTracker.isComplete(fileId, totalChunks));
    }

    @Override
    public Mono<Boolean> isBatchReady(UUID fileId, int startChunk, int endChunk) {
        return Mono.just(inMemoryTracker.isBatchReady(fileId, startChunk, endChunk));
    }

    @Override
    public Mono<Boolean> claimBatch(UUID fileId, int batchIndex) {
        return Mono.just(inMemoryTracker.claimBatch(fileId, batchIndex));
    }

    @Override
    public Mono<Boolean> claimCompletion(UUID fileId) {
        return Mono.just(inMemoryTracker.claimCompletion(fileId));
    }

    @Override
    public Mono<Void> cleanupMemory(UUID fileId) {
        return Mono.fromRunnable(() -> inMemoryTracker.cleanup(fileId));
    }
}
