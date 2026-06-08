package io.github.faizul.File.core.googleDrive;

import io.github.faizul.File.core.FileService;
import io.github.faizul.File.dtos.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class GoogleDriveServiceImp implements FileService {
    @Override
    public Mono<FileResponse> findByUUID(UUID uuid) {
        return null;
    }

    @Override
    public Mono<Void> deleteByUUID(UUID uuid) {
        return null;
    }

    @Override
    public Flux<FileResponse> getAllByUserId() {
        return null;
    }

    @Override
    public Flux<FileResponse> getAllForAdmin() {
        return null;
    }

    @Override
    public Mono<UserProfileResponse> getCurrentUserProfile() {
        return null;
    }

    @Override
    public Mono<UserStorageResponse> getCurrentUserStorage() {
        return null;
    }

    @Override
    public Flux<UserStorageSummary> getUserStorageSummary() {
        return null;
    }

    @Override
    public Mono<UserStorageSummary> updateUserQuota(Long id, UpdateQuotaRequest request) {
        return null;
    }
}
