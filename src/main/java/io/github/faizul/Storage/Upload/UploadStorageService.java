package io.github.faizul.Storage.Upload;

import reactor.core.publisher.Mono;

import java.util.UUID;


public interface UploadStorageService {
    Mono<Void> sendBatch(Long userId, UUID fileId, int startChunk, int endChunk);
    Mono<Void> sendFinalSignal(Long userId, UUID fileId, int totalChunks);
    Mono<Void> deleteFile(Long userId, String fileId);
}
