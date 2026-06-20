package io.github.faizul.storage.file.local.controller;

import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.storage.file.dtos.FileResponse;
import io.github.faizul.storage.file.dtos.UpdateQuotaRequest;
import io.github.faizul.storage.file.dtos.UserProfileResponse;
import io.github.faizul.storage.file.dtos.UserStorageResponse;
import io.github.faizul.storage.file.dtos.UserStorageSummary;
import io.github.faizul.storage.file.local.service.StorageNodeFileService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/files")
@RequiredArgsConstructor
public class StorageNodeFileController {

    private final StorageNodeFileService fileService;
    private final CurrentUserContext currentUserContext;

    private Mono<UUID> resolveFileId(String id) {
        return currentUserContext.getUserId()
                .flatMap(userId -> fileService.resolveFileId(id, userId));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<FileResponse>> findById(@PathVariable String id) {
        return resolveFileId(id)
                .flatMap(fileService::findByUUID)
                .map(response -> ResponseEntity.ok().body(response));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteFile(@PathVariable String id, org.springframework.web.server.ServerWebExchange exchange) {
        return resolveFileId(id)
                .flatMap(uuid -> fileService.deleteByUUID(uuid, exchange))
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
            @RequestBody UpdateQuotaRequest request,
            org.springframework.web.server.ServerWebExchange exchange) {
        return fileService.updateUserQuota(id, request, exchange)
                .map(ResponseEntity::ok);
    }
}
