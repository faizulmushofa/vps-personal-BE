package io.github.faizul.folder.share;

import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.folder.core.dtos.FolderContentResponse;
import io.github.faizul.folder.share.dtos.*;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FolderSharedService {
    Mono<SharedFolderResponse> shareFolder(ShareFolderRequest request, ServerWebExchange exchange);
    Mono<SharedFolderResponse> updateExpiry(String shareToken, UpdateShareExpiryRequest request, ServerWebExchange exchange);
    Mono<SharedFolderResponse> updateAccess(String shareToken, UpdateShareAccessRequest request, ServerWebExchange exchange);
    Mono<Void> revokeShare(String shareToken, ServerWebExchange exchange);
    Flux<SharedFolderResponse> getSharedFoldersByMe();
    Mono<FolderContentResponse> getSharedFolderContentsPublic(String shareToken, String folderId, ServerWebExchange exchange);
    Mono<FileResponse> uploadToSharedFolderPublic(String shareToken, String folderId, String fileName, long size, FilePart filePart, ServerWebExchange exchange);
    Mono<Void> deleteFromSharedFolderPublic(String shareToken, String fileId, ServerWebExchange exchange);
}
