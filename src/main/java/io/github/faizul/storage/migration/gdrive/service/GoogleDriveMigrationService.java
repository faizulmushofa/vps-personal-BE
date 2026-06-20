package io.github.faizul.storage.migration.gdrive.service;

import io.github.faizul.storage.migration.dtos.MigrationRequest;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface GoogleDriveMigrationService {
    Mono<UUID> startGoogleDriveMigration(MigrationRequest request);
    Mono<UUID> startGoogleDriveMigrationWithLog(MigrationRequest request, org.springframework.web.server.ServerWebExchange exchange);
}
