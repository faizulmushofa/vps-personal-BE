package io.github.faizul.File.core;

import io.github.faizul.File.dtos.*;

import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.File.dtos.UserProfileResponse;
import io.github.faizul.File.dtos.UserStorageResponse;
import io.github.faizul.File.dtos.UserStorageSummary;
import io.github.faizul.File.dtos.UpdateQuotaRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FileService {
    Mono<FileResponse> findByUUID(UUID uuid);
    Mono<Void> deleteByUUID(UUID uuid);
    Flux<FileResponse> getAllByUserId();
    Flux<FileResponse> getAllForAdmin();
    Mono<UserProfileResponse> getCurrentUserProfile();
    Mono<UserStorageResponse> getCurrentUserStorage();
    Flux<UserStorageSummary> getUserStorageSummary();
    Mono<UserStorageSummary> updateUserQuota(Long id, UpdateQuotaRequest request);
}
