package io.github.faizul.storage.folder.local.service;

import io.github.faizul.storage.folder.dtos.*;
import io.github.faizul.storage.folder.dtos.FolderContentResponse;
import io.github.faizul.storage.folder.dtos.FolderCreateRequest;
import io.github.faizul.storage.folder.dtos.FolderMoveRequest;
import io.github.faizul.storage.folder.dtos.FolderResponse;
import java.util.UUID;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;



public interface StorageNodeFolderService {
    Mono<FolderResponse> createFolder(FolderCreateRequest request, ServerWebExchange exchange);
    Mono<FolderContentResponse> getFolderContents(UUID parentId);
    Mono<Void> moveItem(FolderMoveRequest request, ServerWebExchange exchange);
    Mono<Void> deleteFolder(UUID id, ServerWebExchange exchange);
}
