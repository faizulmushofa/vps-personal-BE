package io.github.faizul.File.upload;

import io.github.faizul.File.dtos.*;
import io.github.faizul.File.core.*;

import io.github.faizul.File.dtos.InitRequest;
import io.github.faizul.File.dtos.InitResponse;
import io.github.faizul.File.dtos.UploadSessionResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UploadService {
    Mono<InitResponse> create(InitRequest request);
    Mono<Void> updateUploadProgress(UUID fileId, int receivedChunks);
    Mono<Void> markAsCompleted(UUID fileId);
    Mono<Void> cancelUpload(UUID fileId);
    Mono<UploadSessionResponse> getStatus(UUID fileId);
}
