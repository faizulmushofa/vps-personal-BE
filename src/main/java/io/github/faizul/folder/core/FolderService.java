package io.github.faizul.folder.core;

import io.github.faizul.folder.core.dtos.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FolderService {
    Mono<FolderResponse> createFolder(FolderCreateRequest request, ServerWebExchange exchange);
    Mono<FolderContentResponse> getFolderContents(UUID parentId);
    Mono<Void> moveItem(FolderMoveRequest request, ServerWebExchange exchange);
    Mono<Void> deleteFolder(UUID id, ServerWebExchange exchange);
}
