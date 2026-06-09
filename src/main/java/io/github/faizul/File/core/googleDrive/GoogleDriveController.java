package io.github.faizul.File.core.googleDrive;

import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.File.dtos.UserStorageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("api/google-drive")
@RequiredArgsConstructor
public class GoogleDriveController {

    private final GoogleDriveServiceImp googleDriveService;

    @PostMapping("/sync")
    public Mono<ResponseEntity<Void>> syncGoogleDrive() {
        return googleDriveService.syncGoogleDrive()
                .thenReturn(ResponseEntity.ok().build());
    }

    @DeleteMapping("/files/{id}")
    public Mono<ResponseEntity<Void>> deleteFile(@PathVariable UUID id) {
        return googleDriveService.deleteFile(id)
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
    public Flux<FileResponse> getFiles() {
        return googleDriveService.getFiles();
    }

    @GetMapping("/storage")
    public Mono<UserStorageResponse> getStorage() {
        return googleDriveService.getStorage();
    }
}
