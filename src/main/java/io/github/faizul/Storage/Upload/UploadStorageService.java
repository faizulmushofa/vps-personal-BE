package io.github.faizul.Storage.Upload;

import reactor.core.publisher.Mono;

import java.util.UUID;


public interface UploadStorageService {
    Mono<Void> sendBatch(UUID fileId, int startChunk, int endChunk);
    Mono<Void> sendFinalSignal(UUID fileId, int totalChunks);
    Mono<Void> deleteFile(String fileId);
}
