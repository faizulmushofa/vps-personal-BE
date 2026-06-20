package io.github.faizul.storage.folder.gdrive.service.impl;

import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.storage.file.service.client.GoogleDriveClient;
import io.github.faizul.storage.folder.dtos.GoogleDriveFolderContentResponse;
import io.github.faizul.storage.folder.dtos.GoogleDriveItemResponse;
import io.github.faizul.storage.folder.gdrive.service.GoogleDriveFolderService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleDriveFolderServiceImpl implements GoogleDriveFolderService {

    private final GoogleDriveClient googleDriveClient;
    private final CurrentUserContext currentUserContext;
    private final UserActivityService userActivityService;

    @Override
    public Mono<String> createFolder(Long externalAccountId, String name, String parentId, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> googleDriveClient.createFolder(externalAccountId, name, parentId)
                        .flatMap(folderId -> userActivityService.log(
                                userId,
                                "CREATE_FOLDER_GD",
                                "Membuat folder Google Drive baru: " + name,
                                exchange
                        ).thenReturn(folderId)));
    }

    @Override
    public Mono<GoogleDriveFolderContentResponse> getFolderContents(Long externalAccountId, String parentId) {
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
                    return new GoogleDriveFolderContentResponse(items);
                });
    }

    @Override
    public Mono<Void> moveItem(Long externalAccountId, String fileId, String targetFolderId, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> googleDriveClient.moveFile(externalAccountId, fileId, targetFolderId)
                        .then(userActivityService.log(
                                userId,
                                "MOVE_FILE_GD",
                                "Memindahkan file/folder Google Drive: ID " + fileId + " ke folder " + (targetFolderId != null ? targetFolderId : "Root"),
                                exchange
                        )))
                .then();
    }

    @Override
    public Mono<Void> deleteFolder(Long externalAccountId, String folderId, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> googleDriveClient.deleteFile(externalAccountId, folderId)
                        .then(userActivityService.log(
                                userId,
                                "DELETE_FOLDER_GD",
                                "Menghapus folder Google Drive ID: " + folderId,
                                exchange
                        )))
                .then();
    }
}
