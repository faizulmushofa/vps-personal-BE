package io.github.faizul.migration.storageNode;

import io.github.faizul.migration.dtos.MigrationRequest;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface StorageNodeMigrationService {
    Mono<UUID> startStorageNodeMigration(MigrationRequest request);
}
