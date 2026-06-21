package io.github.faizul.storage.download.gdrive.controller;

import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.storage.download.gdrive.service.GoogleDriveDownloadService;
import io.github.faizul.storage.file.dtos.DownloadInitRequest;
import io.github.faizul.storage.file.dtos.DownloadInitResponse;
import io.github.faizul.storage.file.dtos.DownloadStatusResponse;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/google-drive/download")
public class GoogleDriveDownloadController {

    private final GoogleDriveDownloadService downloadService;
    private final CurrentUserContext currentUserContext;

    public GoogleDriveDownloadController(
            GoogleDriveDownloadService downloadService,
            CurrentUserContext currentUserContext) {
        this.downloadService = downloadService;
        this.currentUserContext = currentUserContext;
    }

    @PostMapping("/init")
    public Mono<ResponseEntity<DownloadInitResponse>> init(@RequestBody DownloadInitRequest request) {
        return downloadService.init(request)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{fileId}/stream")
    public Mono<ResponseEntity<Flux<byte[]>>> streamFile(
            @PathVariable String fileId,
            @RequestParam(required = false) Long externalAccountId,
            org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> {
                    boolean isUuid = false;
                    UUID uuid = null;
                    try {
                        uuid = UUID.fromString(fileId);
                        isUuid = true;
                    } catch (IllegalArgumentException e) {
                        // Not a UUID
                    }

                    if (isUuid) {
                        final UUID finalUuid = uuid;
                        return downloadService.getFileDetails(finalUuid)
                                .map(file -> ResponseEntity.ok()
                                        .header("Content-Disposition", "attachment; filename=\"" + file.originalFileName() + "\"")
                                        .header("Content-Length", String.valueOf(file.size()))
                                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                        .body(downloadService.downloadFileWithLog(finalUuid, userId, exchange))
                                );
                    } else {
                        if (externalAccountId == null) {
                            return Mono.error(new IllegalArgumentException("externalAccountId is required for non-UUID files"));
                        }
                        return downloadService.getExternalFileMetadata(externalAccountId, fileId, userId)
                                .map(metadata -> {
                                    String name = (String) metadata.get("name");
                                    Object sizeObj = metadata.get("size");
                                    long size = 0;
                                    if (sizeObj instanceof Number) {
                                        size = ((Number) sizeObj).longValue();
                                    } else if (sizeObj instanceof String) {
                                        size = Long.parseLong((String) sizeObj);
                                    }
                                    return ResponseEntity.ok()
                                            .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                                            .header("Content-Length", String.valueOf(size))
                                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                            .body(downloadService.downloadExternalFile(externalAccountId, fileId, userId, exchange));
                                });
                    }
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{fileId}/status")
    public Mono<ResponseEntity<DownloadStatusResponse>> getStatus(
            @PathVariable String fileId,
            org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> downloadService.resolveFileId(fileId, userId)
                        .flatMap(downloadService::getStatus)
                        .map(ResponseEntity::ok));
    }

    @PostMapping("/{fileId}/cancel")
    public Mono<ResponseEntity<Void>> cancel(
            @PathVariable String fileId,
            org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> downloadService.resolveFileId(fileId, userId)
                        .flatMap(downloadService::cancel)
                        .thenReturn(ResponseEntity.ok().<Void>build()));
    }
}
