package io.github.faizul.storage.file.local.service;

import io.github.faizul.storage.file.dtos.*;

import io.github.faizul.storage.file.dtos.FileResponse;
import io.github.faizul.storage.file.dtos.UserProfileResponse;
import io.github.faizul.storage.file.dtos.UserStorageResponse;
import io.github.faizul.storage.file.dtos.UserStorageSummary;
import io.github.faizul.storage.file.dtos.UpdateQuotaRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface StorageNodeFileService {
    Mono<FileResponse> findByUUID(UUID uuid);
    Mono<String> deleteByUUID(UUID uuid, org.springframework.web.server.ServerWebExchange exchange);
    Mono<UUID> resolveFileId(String fileIdString, Long userId);
    Flux<FileResponse> getAllByUserId();
    Flux<FileResponse> getAllForAdmin();
    Mono<UserProfileResponse> getCurrentUserProfile();
    Mono<UserStorageResponse> getCurrentUserStorage();
    Flux<UserStorageSummary> getUserStorageSummary();
    Mono<UserStorageSummary> updateUserQuota(Long id, UpdateQuotaRequest request, org.springframework.web.server.ServerWebExchange exchange);
}
