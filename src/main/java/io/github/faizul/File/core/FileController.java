package io.github.faizul.File.core;

import io.github.faizul.File.dtos.*;

import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.File.dtos.UserProfileResponse;
import io.github.faizul.File.dtos.UserStorageResponse;
import io.github.faizul.File.dtos.UserStorageSummary;
import io.github.faizul.File.dtos.UpdateQuotaRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @GetMapping("/{id}")
    public Mono<ResponseEntity<FileResponse>> findById(@PathVariable UUID id) {
        return fileService.findByUUID(id)
                .map(response -> ResponseEntity.ok().body(response));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteFile(@PathVariable UUID id) {
        return fileService.deleteByUUID(id)
                .thenReturn(ResponseEntity.noContent().build());
    }

    @GetMapping
    public Flux<FileResponse> getAllByUserId() {
        return fileService.getAllByUserId();
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<FileResponse> getAllForAdmin() {
        return fileService.getAllForAdmin();
    }

    @GetMapping("/me")
    public Mono<UserProfileResponse> getCurrentUserProfile() {
        return fileService.getCurrentUserProfile();
    }

    @GetMapping("/me/storage")
    public Mono<UserStorageResponse> getCurrentUserStorage() {
        return fileService.getCurrentUserStorage();
    }

    @GetMapping("/storage-summary")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ADMIN')")
    public Flux<UserStorageSummary> getUserStorageSummary() {
        return fileService.getUserStorageSummary();
    }

    @PutMapping("/users/{id}/quota")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ADMIN')")
    public Mono<ResponseEntity<UserStorageSummary>> updateUserQuota(
            @PathVariable Long id,
            @RequestBody UpdateQuotaRequest request) {
        return fileService.updateUserQuota(id, request)
                .map(ResponseEntity::ok);
    }
}
