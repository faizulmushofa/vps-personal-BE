package io.github.faizul.storage.migration.gdrive.controller;

import io.github.faizul.storage.migration.dtos.MigrationRequest;
import io.github.faizul.storage.migration.gdrive.service.GoogleDriveMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("api/migrations/drive")
@RequiredArgsConstructor
public class GoogleDriveMigrationController {

    private final GoogleDriveMigrationService googleDriveMigrationService;

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> startDriveMigration(
            @RequestBody MigrationRequest request,
            ServerWebExchange exchange) {
        return googleDriveMigrationService.startGoogleDriveMigrationWithLog(request, exchange)
                .map(batchId -> ResponseEntity.ok().body(Map.of(
                        "success", true,
                        "batchId", batchId
                )));
    }
}
