package io.github.faizul.UploadUnit;

import reactor.core.publisher.Mono;

import java.util.UUID;


public interface IOCleaningService {

    Mono<Void> cleanupBatch(UUID fileId, int startChunk, int endChunk);
    Mono<Void> cleanupTempFiles(UUID fileId);

}
