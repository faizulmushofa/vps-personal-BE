package io.github.faizul.migration.drive;

import io.github.faizul.migration.dtos.MigrationRequest;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface GoogleDriveMigrationService {
    Mono<UUID> startGoogleDriveMigration(MigrationRequest request);
}
