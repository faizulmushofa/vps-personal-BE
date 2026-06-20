package io.github.faizul.storage.upload.service;

import io.github.faizul.storage.file.dtos.*;
import io.github.faizul.storage.file.model.*;

import io.github.faizul.storage.file.dtos.InitRequest;
import io.github.faizul.storage.file.dtos.InitResponse;
import io.github.faizul.storage.file.dtos.UploadSessionResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UploadService {
    Mono<InitResponse> create(InitRequest request);
    Mono<Void> updateUploadProgress(UUID fileId, int receivedChunks);
    Mono<Void> markAsCompleted(UUID fileId);
    Mono<Void> cancelUpload(UUID fileId);
    Mono<UploadSessionResponse> getStatus(UUID fileId);
}
