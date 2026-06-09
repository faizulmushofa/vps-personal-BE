package io.github.faizul.File.upload.GoogleDriveUpload;

import io.github.faizul.File.dtos.InitRequest;
import io.github.faizul.File.dtos.InitResponse;
import io.github.faizul.File.dtos.UploadSessionResponse;
import io.github.faizul.File.upload.UploadService;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class UploadGoogleDriveServiceImp implements UploadService {
    @Override
    public Mono<InitResponse> create(InitRequest request) {
        return null;
    }

    @Override
    public Mono<Void> updateUploadProgress(UUID fileId, int receivedChunks) {
        return null;
    }

    @Override
    public Mono<Void> markAsCompleted(UUID fileId) {
        return null;
    }

    @Override
    public Mono<Void> cancelUpload(UUID fileId) {
        return null;
    }

    @Override
    public Mono<UploadSessionResponse> getStatus(UUID fileId) {
        return null;
    }
}
