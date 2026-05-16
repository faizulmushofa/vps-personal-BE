package io.github.faizul.UploadUnit;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Bertanggung jawab penuh atas siklus hidup file fisik sementara (temp) di hardisk lokal.
 */
public interface IOCleaningService {
    Mono<Void> cleanupBatch(UUID fileId, int startChunk, int endChunk);
    Mono<Void> cleanupTempFiles(UUID fileId);
}
