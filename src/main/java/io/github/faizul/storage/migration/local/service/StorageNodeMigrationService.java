package io.github.faizul.storage.migration.local.service;

import io.github.faizul.storage.migration.dtos.MigrationRequest;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface StorageNodeMigrationService {
    Mono<UUID> startStorageNodeMigration(MigrationRequest request);
    Mono<UUID> startStorageNodeMigrationWithLog(MigrationRequest request, org.springframework.web.server.ServerWebExchange exchange);
}
