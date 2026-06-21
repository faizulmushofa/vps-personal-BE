package io.github.faizul.storage.migration.local.controller;

import io.github.faizul.storage.migration.dtos.MigrationRequest;
import io.github.faizul.storage.migration.local.service.StorageNodeMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("api/migrations/storage-node")
@RequiredArgsConstructor
public class StorageNodeMigrationController {

    private final StorageNodeMigrationService storageNodeMigrationService;

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> startStorageNodeMigration(
            @RequestBody MigrationRequest request,
            ServerWebExchange exchange) {
        return storageNodeMigrationService.startStorageNodeMigrationWithLog(request, exchange)
                .map(batchId -> ResponseEntity.ok().body(Map.of(
                        "success", true,
                        "batchId", batchId
                )));
    }
}
