package io.github.faizul.storage.folder.gdrive.service;

import io.github.faizul.storage.folder.dtos.GoogleDriveFolderContentResponse;
import reactor.core.publisher.Mono;

public interface GoogleDriveFolderService {
    Mono<String> createFolder(Long externalAccountId, String name, String parentId, org.springframework.web.server.ServerWebExchange exchange);
    Mono<GoogleDriveFolderContentResponse> getFolderContents(Long externalAccountId, String parentId);
    Mono<Void> moveItem(Long externalAccountId, String fileId, String targetFolderId, org.springframework.web.server.ServerWebExchange exchange);
    Mono<Void> deleteFolder(Long externalAccountId, String folderId, org.springframework.web.server.ServerWebExchange exchange);
}
