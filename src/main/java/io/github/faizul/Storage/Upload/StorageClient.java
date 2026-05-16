package io.github.faizul.Storage.Upload;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Port/Interface untuk berkomunikasi dengan Storage Service (contoh: via gRPC nantinya).
 * Ini memisahkan UploadCoordinator dari detail implementasi gRPC/Storage.
 */
public interface StorageClient {
    Mono<Void> sendBatch(UUID fileId, int startChunk, int endChunk);
    Mono<Void> sendFinalSignal(UUID fileId);
}
