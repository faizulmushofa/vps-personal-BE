package io.github.faizul.File.share.GoogleDrive;

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
@RequestMapping("api/google-drive/share")
public class GoogleDriveShareController {

    private final ShareService shareService;

    public GoogleDriveShareController(@Qualifier("googleDriveShareService") ShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping("/{fileId}")
    public Mono<ResponseEntity<ShareFileResponse>> shareFile(@PathVariable UUID fileId, @RequestBody ShareFileRequest request) {
        return shareService.shareFile(fileId, request)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{fileId}/{userId}")
    public Mono<ResponseEntity<Void>> unshareFile(@PathVariable UUID fileId, @PathVariable Long userId) {
        return shareService.unshareFile(fileId, userId)
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
    public Mono<ResponseEntity<Void>> unshareFileById(@PathVariable Long shareId) {
        return shareService.unshareFile(shareId)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
