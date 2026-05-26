package io.github.faizul.File.FileService;

import io.github.faizul.File.Dtos.FileResponse;
import io.github.faizul.File.Dtos.UserProfileResponse;
import io.github.faizul.File.Dtos.UserStorageResponse;
import io.github.faizul.File.Dtos.UserStorageSummary;
import io.github.faizul.File.Dtos.UpdateQuotaRequest;
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
