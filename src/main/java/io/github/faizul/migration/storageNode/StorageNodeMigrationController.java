package io.github.faizul.migration.storageNode;

import io.github.faizul.migration.dtos.MigrationRequest;
import io.github.faizul.activity.UserActivityService;
import io.github.faizul.security.filter.CurrentUserContext;
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
    private final UserActivityService userActivityService;
    private final CurrentUserContext currentUserContext;

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> startStorageNodeMigration(
            @RequestBody MigrationRequest request,
            ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> userActivityService.log(
                        userId,
                        "MIGRATION_START",
                        "Memulai migrasi berkas massal ke VPS Storage Node (" + (request.fileIds() != null ? request.fileIds().size() : 0) + " berkas)",
                        exchange)
                        .then(storageNodeMigrationService.startStorageNodeMigration(request))
                )
                .map(batchId -> ResponseEntity.ok().body(Map.of(
                        "success", true,
                        "batchId", batchId
                )));
    }
}

