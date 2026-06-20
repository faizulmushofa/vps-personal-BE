package io.github.faizul.storage.migration.controller;

import io.github.faizul.storage.migration.model.MigrationTask;
import io.github.faizul.storage.migration.service.MigrationService;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;



@RestController
@RequestMapping("api/migrations")
@RequiredArgsConstructor
public class MigrationController {

    private final MigrationService migrationService;

    @GetMapping("/config")
    public Mono<ResponseEntity<Map<String, Object>>> getMigrationConfig() {
        return migrationService.getMigrationConfig()
                .map(ResponseEntity::ok);
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ADMIN')")
    public Mono<ResponseEntity<Map<String, Object>>> updateMigrationConfig(@RequestBody Map<String, String> newSettings) {
        return migrationService.updateMigrationConfig(newSettings)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/tasks")
    public Flux<MigrationTask> getTasks(@RequestParam(required = false) UUID batchId) {
        return migrationService.getTasks(batchId);
    }

    @PostMapping("/tasks/{id}/cancel")
    public Mono<ResponseEntity<Map<String, Object>>> cancelTask(@PathVariable UUID id) {
        return migrationService.cancelTask(id)
                .thenReturn(ResponseEntity.ok(Map.of("success", true)));
    }

    @PostMapping("/tasks/cancel")
    public Mono<ResponseEntity<Map<String, Object>>> cancelTaskByBatchAndFile(
            @RequestParam UUID batchId,
            @RequestParam String fileId) {
        return migrationService.cancelTaskByBatchIdAndFileId(batchId, fileId)
                .thenReturn(ResponseEntity.ok(Map.of("success", true)));
    }
}

