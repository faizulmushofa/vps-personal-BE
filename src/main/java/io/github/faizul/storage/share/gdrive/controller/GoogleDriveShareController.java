package io.github.faizul.storage.share.gdrive.controller;

import io.github.faizul.storage.file.dtos.FileResponse;
import io.github.faizul.storage.file.dtos.ShareFileRequest;
import io.github.faizul.storage.file.dtos.ShareFileResponse;
import io.github.faizul.storage.file.dtos.SharedByMeResponse;
import io.github.faizul.storage.share.gdrive.service.GoogleDriveShareService;
import io.github.faizul.storage.share.local.service.FolderSharedService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/google-drive/share")
public class GoogleDriveShareController {

    private final GoogleDriveShareService shareService;
    private final FolderSharedService folderSharedService;

    public GoogleDriveShareController(
            GoogleDriveShareService shareService,
            FolderSharedService folderSharedService) {
        this.shareService = shareService;
        this.folderSharedService = folderSharedService;
    }

    @PostMapping("/{fileId}")
    public Mono<ResponseEntity<ShareFileResponse>> shareFile(@PathVariable String fileId, @RequestBody ShareFileRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return shareService.shareFileWithLog(fileId, request, exchange)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{fileId}/{userId}")
    public Mono<ResponseEntity<Void>> unshareFile(@PathVariable String fileId, @PathVariable Long userId, org.springframework.web.server.ServerWebExchange exchange) {
        return shareService.unshareFileWithLog(fileId, userId, exchange)
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
            @RequestParam(value = "download", required = false, defaultValue = "false") Boolean download,
            @RequestParam(value = "fileId", required = false) String fileId,
            org.springframework.web.server.ServerWebExchange exchange) {
        
        if (fileId == null || fileId.trim().isEmpty()) {
            return shareService.getPublicFileInfo(shareToken)
                    .flatMap(file -> {
                        String disposition = Boolean.TRUE.equals(download)
                                ? "attachment; filename=\"" + file.originalFileName() + "\""
                                : "inline; filename=\"" + file.originalFileName() + "\"";

                        String contentType = org.springframework.http.MediaTypeFactory.getMediaType(file.originalFileName())
                                .map(org.springframework.http.MediaType::toString)
                                .orElse("application/octet-stream");

                        return Mono.just(ResponseEntity.ok()
                                .header("Content-Disposition", disposition)
                                .header("Content-Length", String.valueOf(file.size()))
                                .contentType(MediaType.parseMediaType(contentType))
                                .body(shareService.downloadPublicFileWithLog(shareToken, exchange)));
                    })
                    .defaultIfEmpty(ResponseEntity.notFound().build());
        }

        // Downloading file from a shared folder
        return folderSharedService.getSharedFileMetadataPublic(shareToken, fileId)
                .flatMap(file -> {
                    String disposition = Boolean.TRUE.equals(download)
                            ? "attachment; filename=\"" + file.originalFileName() + "\""
                            : "inline; filename=\"" + file.originalFileName() + "\"";

                    String contentType = org.springframework.http.MediaTypeFactory.getMediaType(file.originalFileName())
                            .map(org.springframework.http.MediaType::toString)
                            .orElse("application/octet-stream");

                    Flux<byte[]> dataStream = folderSharedService.downloadFileFromSharedFolderPublic(shareToken, fileId, exchange);

                    return Mono.just(ResponseEntity.ok()
                            .header("Content-Disposition", disposition)
                            .header("Content-Length", String.valueOf(file.size()))
                            .contentType(MediaType.parseMediaType(contentType))
                            .body(dataStream));
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/shared-by-me")
    public Flux<SharedByMeResponse> getSharedByMe() {
        return shareService.getSharedByMe();
    }

    @DeleteMapping("/cancel/{shareId}")
    public Mono<ResponseEntity<Void>> unshareFileById(@PathVariable Long shareId, org.springframework.web.server.ServerWebExchange exchange) {
        return shareService.unshareFileByIdWithLog(shareId, exchange)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
