package io.github.faizul.folder.core;

import io.github.faizul.File.core.googleDrive.GoogleDriveClient;
import io.github.faizul.activity.UserActivityService;
import io.github.faizul.folder.core.dtos.*;
import io.github.faizul.security.filter.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api/google-drive/folders")
@RequiredArgsConstructor
@Slf4j
public class GoogleDriveFolderController {

    private final GoogleDriveClient googleDriveClient;
    private final CurrentUserContext currentUserContext;
    private final UserActivityService userActivityService;

    @PostMapping
    public Mono<ResponseEntity<Map<String, String>>> createFolder(
            @RequestBody GoogleDriveFolderCreateRequest request,
            ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> googleDriveClient.createFolder(request.externalAccountId(), request.name(), request.parentId())
                        .flatMap(folderId -> userActivityService.log(
                                userId,
                                "CREATE_FOLDER_GD",
                                "Membuat folder Google Drive baru: " + request.name(),
                                exchange
                        ).thenReturn(ResponseEntity.ok(Map.of("id", folderId)))));
    }

    @GetMapping("/contents")
    public Mono<ResponseEntity<GoogleDriveFolderContentResponse>> getFolderContents(
            @RequestParam Long externalAccountId,
            @RequestParam(required = false) String parentId) {
        return googleDriveClient.listFilesAndFolders(externalAccountId, parentId)
                .map(list -> {
                    List<GoogleDriveItemResponse> items = list.stream()
                            .map(map -> {
                                String id = (String) map.get("id");
                                String name = (String) map.get("name");
                                String mimeType = (String) map.get("mimeType");
                                String createdTime = (String) map.get("createdTime");
                                Long size = null;
                                if (map.get("size") != null) {
                                    size = Long.parseLong(map.get("size").toString());
                                }
                                return new GoogleDriveItemResponse(id, name, size, mimeType, createdTime);
                            })
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(new GoogleDriveFolderContentResponse(items));
                });
    }

    @PostMapping("/move")
    public Mono<ResponseEntity<Void>> moveItem(
            @RequestBody GoogleDriveFolderMoveRequest request,
            ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> googleDriveClient.moveFile(request.externalAccountId(), request.fileId(), request.targetFolderId())
                        .then(userActivityService.log(
                                userId,
                                "MOVE_FILE_GD",
                                "Memindahkan file/folder Google Drive: ID " + request.fileId() + " ke folder " + (request.targetFolderId() != null ? request.targetFolderId() : "Root"),
                                exchange
                        ).then(Mono.just(ResponseEntity.ok().<Void>build()))));
    }

    @DeleteMapping("/{folderId}")
    public Mono<ResponseEntity<Void>> deleteFolder(
            @PathVariable String folderId,
            @RequestParam Long externalAccountId,
            ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> googleDriveClient.deleteFile(externalAccountId, folderId)
                        .then(userActivityService.log(
                                userId,
                                "DELETE_FOLDER_GD",
                                "Menghapus folder Google Drive ID: " + folderId,
                                exchange
                        ).then(Mono.just(ResponseEntity.noContent().build()))));
    }
}
