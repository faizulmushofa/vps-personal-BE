package io.github.faizul.File.share.StorageNode;

import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.File.dtos.ShareFileRequest;
import io.github.faizul.File.dtos.ShareFileResponse;
import io.github.faizul.File.share.ShareService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("api/files/share")
public class StorageNodeShareController {

    private final ShareService shareService;
    private final io.github.faizul.security.filter.CurrentUserContext currentUserContext;
    private final io.github.faizul.activity.UserActivityService userActivityService;
    private final io.github.faizul.File.core.FileRepository fileRepository;

    public StorageNodeShareController(
            @Qualifier("storageNodeShareService") ShareService shareService,
            io.github.faizul.security.filter.CurrentUserContext currentUserContext,
            io.github.faizul.activity.UserActivityService userActivityService,
            io.github.faizul.File.core.FileRepository fileRepository) {
        this.shareService = shareService;
        this.currentUserContext = currentUserContext;
        this.userActivityService = userActivityService;
        this.fileRepository = fileRepository;
    }

    @PostMapping("/{fileId}")
    public Mono<ResponseEntity<ShareFileResponse>> shareFile(@PathVariable UUID fileId, @RequestBody ShareFileRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> fileRepository.findById(fileId)
                        .flatMap(file -> shareService.shareFile(fileId, request)
                                .flatMap(response -> userActivityService.log(userId, "SHARE_FILE", "Membagikan berkas: " + file.getOriginalFileName(), exchange)
                                        .thenReturn(response)
                                )
                        )
                )
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{fileId}/{userId}")
    public Mono<ResponseEntity<Void>> unshareFile(@PathVariable UUID fileId, @PathVariable Long userId, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(adminId -> fileRepository.findById(fileId)
                        .flatMap(file -> shareService.unshareFile(fileId, userId)
                                .then(userActivityService.log(adminId, "UNSHARE_FILE", "Membatalkan share berkas " + file.getOriginalFileName() + " untuk user ID: " + userId, exchange))
                        )
                )
                .thenReturn(ResponseEntity.noContent().build());
    }

    @GetMapping("/shared-with-me")
    public Flux<FileResponse> getSharedWithMe() {
        return shareService.getSharedWithMe();
    }

    @GetMapping("/public/info/{shareToken}")
    public Mono<ResponseEntity<FileResponse>> getPublicFileInfo(@PathVariable String shareToken) {
        return shareService.getPublicFileInfo(shareToken)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/public/download/{shareToken}")
    public Mono<ResponseEntity<Flux<byte[]>>> downloadPublicFile(
            @PathVariable String shareToken,
            @RequestParam(value = "download", required = false, defaultValue = "false") Boolean download) {
        return shareService.getPublicFileInfo(shareToken)
                .map(file -> {
                    String disposition = Boolean.TRUE.equals(download)
                            ? "attachment; filename=\"" + file.originalFileName() + "\""
                            : "inline; filename=\"" + file.originalFileName() + "\"";

                    String contentType = org.springframework.http.MediaTypeFactory.getMediaType(file.originalFileName())
                            .map(org.springframework.http.MediaType::toString)
                            .orElse("application/octet-stream");

                    return ResponseEntity.ok()
                            .header("Content-Disposition", disposition)
                            .header("Content-Length", String.valueOf(file.size()))
                            .contentType(MediaType.parseMediaType(contentType))
                            .body(shareService.downloadPublicFile(shareToken));
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/shared-by-me")
    public Flux<io.github.faizul.File.dtos.SharedByMeResponse> getSharedByMe() {
        return shareService.getSharedByMe();
    }

    @DeleteMapping("/cancel/{shareId}")
    public Mono<ResponseEntity<Void>> unshareFileById(@PathVariable Long shareId, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> shareService.unshareFile(shareId)
                        .then(userActivityService.log(userId, "CANCEL_SHARE", "Membatalkan share berkas dengan Share ID: " + shareId, exchange))
                )
                .thenReturn(ResponseEntity.noContent().build());
    }
}
