package io.github.faizul.storage.folder.local.controller;

import io.github.faizul.storage.folder.dtos.*;
import io.github.faizul.storage.folder.dtos.FolderContentResponse;
import io.github.faizul.storage.folder.dtos.FolderCreateRequest;
import io.github.faizul.storage.folder.dtos.FolderMoveRequest;
import io.github.faizul.storage.folder.dtos.FolderResponse;
import io.github.faizul.storage.folder.local.service.StorageNodeFolderService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;



@RestController
@RequestMapping("api/folders")
@RequiredArgsConstructor
public class StorageNodeFolderController {

    private final StorageNodeFolderService folderService;

    @PostMapping
    public Mono<ResponseEntity<FolderResponse>> createFolder(
            @RequestBody FolderCreateRequest request,
            ServerWebExchange exchange) {
        return folderService.createFolder(request, exchange)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/contents")
    public Mono<ResponseEntity<FolderContentResponse>> getFolderContents(
            @RequestParam(required = false) UUID parentId) {
        return folderService.getFolderContents(parentId)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/move")
    public Mono<ResponseEntity<Void>> moveItem(
            @RequestBody FolderMoveRequest request,
            ServerWebExchange exchange) {
        return folderService.moveItem(request, exchange)
                .thenReturn(ResponseEntity.ok().build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteFolder(
            @PathVariable UUID id,
            ServerWebExchange exchange) {
        return folderService.deleteFolder(id, exchange)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
