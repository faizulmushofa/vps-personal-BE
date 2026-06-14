package io.github.faizul.migration;

import io.github.faizul.File.core.File;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MigrationService {
    Mono<Map<String, Object>> getMigrationConfig();
    Mono<Map<String, Object>> updateMigrationConfig(Map<String, String> newSettings);
    Flux<MigrationTask> getTasks(UUID batchId);
    
    // Shared validation & status update utilities
    Mono<Void> validateCommonLimits(Long userId);
    Mono<List<File>> validateAndGetSourceFiles(List<UUID> fileIds, Long userId, Long maxFileSizeBytes, String targetProvider, Long targetExternalAccountId);
    Mono<Void> updateTaskProgress(UUID taskId, double progress);
    Mono<Void> updateTaskStatus(UUID taskId, MigrationStatus status, double progress, String errorMessage);
}
