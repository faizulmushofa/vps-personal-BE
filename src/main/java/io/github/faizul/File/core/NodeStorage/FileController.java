package io.github.faizul.File.core.NodeStorage;

import io.github.faizul.File.core.FileService;

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
    private final io.github.faizul.security.filter.CurrentUserContext currentUserContext;
    private final io.github.faizul.activity.UserActivityService userActivityService;
    private final io.github.faizul.File.core.FileRepository fileRepository;

    @GetMapping("/{id}")
    public Mono<ResponseEntity<FileResponse>> findById(@PathVariable UUID id) {
        return fileService.findByUUID(id)
                .map(response -> ResponseEntity.ok().body(response));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteFile(@PathVariable UUID id, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> fileRepository.findById(id)
                        .flatMap(file -> fileService.deleteByUUID(id)
                                .then(userActivityService.log(userId, "DELETE_FILE", "Menghapus berkas: " + file.getOriginalFileName(), exchange))
                        )
                )
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
        return currentUserContext.getUserId()
                .flatMap(adminId -> fileService.updateUserQuota(id, request)
                        .flatMap(summary -> userActivityService.log(adminId, "UPDATE_USER_QUOTA", 
                                "Mengubah kuota penyimpanan user " + summary.username() + " (ID: " + id + ") menjadi " + request.quotaBytes() + " bytes", exchange)
                                .thenReturn(summary)
                        )
                )
                .map(ResponseEntity::ok);
    }
}
