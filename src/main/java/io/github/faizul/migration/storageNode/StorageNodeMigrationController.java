package io.github.faizul.migration.storageNode;

import io.github.faizul.migration.dtos.MigrationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("api/migrations/storage-node")
@RequiredArgsConstructor
public class StorageNodeMigrationController {

    private final StorageNodeMigrationService storageNodeMigrationService;

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> startStorageNodeMigration(@RequestBody MigrationRequest request) {
        return storageNodeMigrationService.startStorageNodeMigration(request)
                .map(batchId -> ResponseEntity.ok().body(Map.of(
                        "success", true,
                        "batchId", batchId
                )));
    }
}
