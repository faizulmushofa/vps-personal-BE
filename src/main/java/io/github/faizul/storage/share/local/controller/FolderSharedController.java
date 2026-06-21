package io.github.faizul.storage.share.local.controller;

import io.github.faizul.storage.file.dtos.FileResponse;
import io.github.faizul.storage.folder.dtos.FolderContentResponse;
import io.github.faizul.storage.share.dtos.*;
import io.github.faizul.storage.share.dtos.ShareFolderRequest;
import io.github.faizul.storage.share.dtos.SharedFolderResponse;
import io.github.faizul.storage.share.dtos.UpdateShareAccessRequest;
import io.github.faizul.storage.share.dtos.UpdateShareExpiryRequest;
import io.github.faizul.storage.share.local.service.FolderSharedService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;



@RestController
@RequestMapping("api/shared-folders")
@RequiredArgsConstructor
public class FolderSharedController {

    private final FolderSharedService folderSharedService;

    @PostMapping
    public Mono<ResponseEntity<SharedFolderResponse>> shareFolder(
            @RequestBody ShareFolderRequest request,
            ServerWebExchange exchange) {
        return folderSharedService.shareFolder(request, exchange)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{shareToken}/expiry")
    public Mono<ResponseEntity<SharedFolderResponse>> updateExpiry(
            @PathVariable String shareToken,
            @RequestBody UpdateShareExpiryRequest request,
            ServerWebExchange exchange) {
        return folderSharedService.updateExpiry(shareToken, request, exchange)
                .map(ResponseEntity::ok);
    }

    @PutMapping("/{shareToken}/access")
    public Mono<ResponseEntity<SharedFolderResponse>> updateAccess(
            @PathVariable String shareToken,
            @RequestBody UpdateShareAccessRequest request,
            ServerWebExchange exchange) {
        return folderSharedService.updateAccess(shareToken, request, exchange)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{shareToken}")
    public Mono<ResponseEntity<Void>> revokeShare(
            @PathVariable String shareToken,
            ServerWebExchange exchange) {
        return folderSharedService.revokeShare(shareToken, exchange)
                .thenReturn(ResponseEntity.noContent().build());
    }

    @GetMapping
    public Flux<SharedFolderResponse> getSharedFoldersByMe() {
        return folderSharedService.getSharedFoldersByMe();
    }

    @GetMapping("/public/{shareToken}/contents")
    public Mono<ResponseEntity<FolderContentResponse>> getSharedFolderContentsPublic(
            @PathVariable String shareToken,
            @RequestParam(required = false) String folderId,
            ServerWebExchange exchange) {
        return folderSharedService.getSharedFolderContentsPublic(shareToken, folderId, exchange)
                .map(ResponseEntity::ok);
    }

    @PostMapping(value = "/public/{shareToken}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<FileResponse>> uploadToSharedFolderPublic(
            @PathVariable String shareToken,
            @RequestPart("file") FilePart filePart,
            @RequestPart("size") String sizeStr,
            @RequestParam(required = false) String folderId,
            ServerWebExchange exchange) {
        long size = Long.parseLong(sizeStr);
        String fileName = filePart.filename();
        return folderSharedService.uploadToSharedFolderPublic(shareToken, folderId, fileName, size, filePart, exchange)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/public/{shareToken}/files/{fileId}")
    public Mono<ResponseEntity<Void>> deleteFromSharedFolderPublic(
            @PathVariable String shareToken,
            @PathVariable String fileId,
            ServerWebExchange exchange) {
        return folderSharedService.deleteFromSharedFolderPublic(shareToken, fileId, exchange)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
