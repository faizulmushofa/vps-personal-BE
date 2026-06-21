package io.github.faizul.storage.download.local.controller;

import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.storage.download.local.service.StorageNodeDownloadService;
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
@RequestMapping("api/files/download")
public class StorageNodeDownloadController {

    private final StorageNodeDownloadService downloadService;
    private final CurrentUserContext currentUserContext;

    public StorageNodeDownloadController(
            StorageNodeDownloadService downloadService,
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
            @PathVariable UUID fileId,
            org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> downloadService.getFileDetails(fileId)
                        .map(file -> ResponseEntity.ok()
                                .header("Content-Disposition", "attachment; filename=\"" + file.originalFileName() + "\"")
                                .header("Content-Length", String.valueOf(file.size()))
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .body(downloadService.downloadFileWithLog(fileId, userId, exchange))
                        )
                )
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{fileId}/status")
    public Mono<ResponseEntity<DownloadStatusResponse>> getStatus(@PathVariable UUID fileId) {
        return downloadService.getStatus(fileId)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{fileId}/cancel")
    public Mono<ResponseEntity<Void>> cancel(@PathVariable UUID fileId) {
        return downloadService.cancel(fileId)
                .thenReturn(ResponseEntity.ok().build());
    }
}
