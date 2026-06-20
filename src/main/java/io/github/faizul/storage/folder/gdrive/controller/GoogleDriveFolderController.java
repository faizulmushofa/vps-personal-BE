package io.github.faizul.storage.folder.gdrive.controller;

import io.github.faizul.storage.folder.dtos.GoogleDriveFolderContentResponse;
import io.github.faizul.storage.folder.dtos.GoogleDriveFolderCreateRequest;
import io.github.faizul.storage.folder.dtos.GoogleDriveFolderMoveRequest;
import io.github.faizul.storage.folder.gdrive.service.GoogleDriveFolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("api/google-drive/folders")
@RequiredArgsConstructor
public class GoogleDriveFolderController {

    private final GoogleDriveFolderService googleDriveFolderService;

    @PostMapping
    public Mono<ResponseEntity<Map<String, String>>> createFolder(
            @RequestBody GoogleDriveFolderCreateRequest request,
            ServerWebExchange exchange) {
        return googleDriveFolderService.createFolder(request.externalAccountId(), request.name(), request.parentId(), exchange)
                .map(folderId -> ResponseEntity.ok(Map.of("id", folderId)));
    }

    @GetMapping("/contents")
    public Mono<ResponseEntity<GoogleDriveFolderContentResponse>> getFolderContents(
            @RequestParam Long externalAccountId,
            @RequestParam(required = false) String parentId) {
        return googleDriveFolderService.getFolderContents(externalAccountId, parentId)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/move")
    public Mono<ResponseEntity<Void>> moveItem(
            @RequestBody GoogleDriveFolderMoveRequest request,
            ServerWebExchange exchange) {
        return googleDriveFolderService.moveItem(request.externalAccountId(), request.fileId(), request.targetFolderId(), exchange)
                .thenReturn(ResponseEntity.ok().build());
    }

    @DeleteMapping("/{folderId}")
    public Mono<ResponseEntity<Void>> deleteFolder(
            @PathVariable String folderId,
            @RequestParam Long externalAccountId,
            ServerWebExchange exchange) {
        return googleDriveFolderService.deleteFolder(externalAccountId, folderId, exchange)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
