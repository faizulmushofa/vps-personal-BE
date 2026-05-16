package io.github.faizul.UploadUnit;

import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UploadCoordinator {
    public Mono<Void> handleChunkUpload(UUID fileId, int chunkIndex, FilePart filePart);
}
