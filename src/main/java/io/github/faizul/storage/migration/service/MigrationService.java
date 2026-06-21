package io.github.faizul.storage.migration.service;

import io.github.faizul.storage.file.model.File;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.github.faizul.storage.migration.model.MigrationStatus;
import io.github.faizul.storage.migration.model.MigrationTask;

public interface MigrationService {
    Mono<Map<String, Object>> getMigrationConfig();
    Mono<Map<String, Object>> updateMigrationConfig(Map<String, String> newSettings);
    Flux<MigrationTask> getTasks(UUID batchId);
    
    // Shared validation & status update utilities
    Mono<Void> validateCommonLimits(Long userId);
    Mono<List<File>> validateAndGetSourceFiles(List<String> fileIds, Long userId, Long maxFileSizeBytes, String targetProvider, Long targetExternalAccountId, Long sourceExternalAccountId);
    Mono<Void> updateTaskProgress(UUID taskId, double progress);
    Mono<Void> updateTaskStatus(UUID taskId, MigrationStatus status, double progress, String errorMessage);
    Mono<Void> cancelTask(UUID taskId);
    Mono<Void> cancelTaskByBatchIdAndFileId(UUID batchId, String fileId);
}

