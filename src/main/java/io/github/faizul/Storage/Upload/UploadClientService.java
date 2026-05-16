package io.github.faizul.Storage.Upload;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class UploadClientService implements StorageClient{
    @Override
    public Mono<Void> sendBatch(UUID fileId, int startChunk, int endChunk) {
        return null;
    }

    @Override
    public Mono<Void> sendFinalSignal(UUID fileId) {
        return null;
    }
}
