package io.github.faizul.migration.drive;

import io.github.faizul.migration.dtos.MigrationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("api/migrations/drive")
@RequiredArgsConstructor
public class GoogleDriveMigrationController {

    private final GoogleDriveMigrationService googleDriveMigrationService;

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> startDriveMigration(@RequestBody MigrationRequest request) {
        return googleDriveMigrationService.startGoogleDriveMigration(request)
                .map(batchId -> ResponseEntity.ok().body(Map.of(
                        "success", true,
                        "batchId", batchId
                )));
    }
}
