package io.github.faizul.storage.upload.local.controller;

import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.storage.file.dtos.InitRequest;
import io.github.faizul.storage.file.dtos.InitResponse;
import io.github.faizul.storage.upload.local.service.StorageNodeUploadService;
import io.github.faizul.storage.uploadunit.service.UploadCoordinator;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/files")
public class StorageNodeUploadController {

    private final StorageNodeUploadService uploadService;
    private final UploadCoordinator uploadCoordinator;
    private final CurrentUserContext currentUserContext;

    public StorageNodeUploadController(
            StorageNodeUploadService uploadService,
            UploadCoordinator uploadCoordinator,
            CurrentUserContext currentUserContext) {
        this.uploadService = uploadService;
        this.uploadCoordinator = uploadCoordinator;
        this.currentUserContext = currentUserContext;
    }

    @PostMapping("/init")
    public Mono<ResponseEntity<InitResponse>> init(@RequestBody InitRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return uploadService.createWithLog(request, exchange)
                .map(response -> ResponseEntity.ok().body(response));
    }

    @PostMapping("/{id}/chunks/{index}")
    public Mono<ResponseEntity<Void>> uploadChunk(
            @PathVariable UUID id,
            @PathVariable int index,
            @RequestPart("file") FilePart filePart
    ) {
        return uploadCoordinator.handleChunkUpload(id, index, filePart)
                .thenReturn(ResponseEntity.accepted().build());
    }

    @PostMapping("/{id}/cancel")
    public Mono<ResponseEntity<Void>> cancelUpload(@PathVariable UUID id, org.springframework.web.server.ServerWebExchange exchange) {
        return uploadService.cancelUploadWithLog(id, exchange)
                .thenReturn(ResponseEntity.ok().build());
    }
}
