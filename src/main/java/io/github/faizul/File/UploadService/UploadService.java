package io.github.faizul.File.UploadService;

import io.github.faizul.File.Dtos.InitRequest;
import io.github.faizul.File.Dtos.InitResponse;
import io.github.faizul.File.Dtos.UploadSessionResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UploadService {
    Mono<InitResponse> create(InitRequest request);
    Mono<Void> updateUploadProgress(UUID fileId, int receivedChunks);
    Mono<Void> markAsCompleted(UUID fileId);
    Mono<Void> cancelUpload(UUID fileId);
    Mono<UploadSessionResponse> getStatus(UUID fileId);
}
