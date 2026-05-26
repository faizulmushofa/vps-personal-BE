package io.github.faizul.UploadUnit;

import reactor.core.publisher.Mono;

import java.util.UUID;


public interface IOCleaningService {

    Mono<Void> cleanupBatch(Long userId, UUID fileId, int startChunk, int endChunk);
    Mono<Void> cleanupTempFiles(Long userId, UUID fileId);

}
