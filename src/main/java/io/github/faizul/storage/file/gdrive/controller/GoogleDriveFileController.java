package io.github.faizul.storage.file.gdrive.controller;

import io.github.faizul.storage.file.dtos.FileResponse;
import io.github.faizul.storage.file.dtos.UserStorageResponse;
import io.github.faizul.storage.file.gdrive.service.GoogleDriveFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController("googleDriveCoreController")
@RequestMapping("api/google-drive")
@RequiredArgsConstructor
public class GoogleDriveFileController {

    private final GoogleDriveFileService googleDriveService;

    @PostMapping("/sync")
    public Mono<ResponseEntity<Void>> syncGoogleDrive(@RequestParam Long externalAccountId, ServerWebExchange exchange) {
        return googleDriveService.syncGoogleDrive(externalAccountId, exchange)
                .thenReturn(ResponseEntity.ok().build());
    }

    @DeleteMapping({"/files/{id}", "/{id}"})
    public Mono<ResponseEntity<Void>> deleteFile(@PathVariable String id, ServerWebExchange exchange) {
        return googleDriveService.deleteFile(id, exchange)
                .map(warning -> {
                    if (warning != null && !warning.isEmpty()) {
                        return ResponseEntity.ok()
                                .header("X-Warning", warning)
                                .<Void>build();
                    }
                    return ResponseEntity.noContent().build();
                });
    }

    @GetMapping("/files")
    public Flux<FileResponse> getFiles(@RequestParam(required = false) Long externalAccountId) {
        return googleDriveService.getFiles(externalAccountId);
    }

    @GetMapping("/storage")
    public Mono<UserStorageResponse> getStorage(@RequestParam Long externalAccountId) {
        return googleDriveService.getStorage(externalAccountId);
    }
}
