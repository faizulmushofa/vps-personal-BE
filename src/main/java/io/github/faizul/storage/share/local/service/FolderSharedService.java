package io.github.faizul.storage.share.local.service;

import io.github.faizul.storage.file.dtos.FileResponse;
import io.github.faizul.storage.folder.dtos.FolderContentResponse;
import io.github.faizul.storage.share.dtos.*;
import io.github.faizul.storage.share.dtos.ShareFolderRequest;
import io.github.faizul.storage.share.dtos.SharedFolderResponse;
import io.github.faizul.storage.share.dtos.UpdateShareAccessRequest;
import io.github.faizul.storage.share.dtos.UpdateShareExpiryRequest;
import java.util.UUID;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;



public interface FolderSharedService {
    Mono<SharedFolderResponse> shareFolder(ShareFolderRequest request, ServerWebExchange exchange);
    Mono<SharedFolderResponse> updateExpiry(String shareToken, UpdateShareExpiryRequest request, ServerWebExchange exchange);
    Mono<SharedFolderResponse> updateAccess(String shareToken, UpdateShareAccessRequest request, ServerWebExchange exchange);
    Mono<Void> revokeShare(String shareToken, ServerWebExchange exchange);
    Flux<SharedFolderResponse> getSharedFoldersByMe();
    Mono<FolderContentResponse> getSharedFolderContentsPublic(String shareToken, String folderId, ServerWebExchange exchange);
    Mono<FileResponse> uploadToSharedFolderPublic(String shareToken, String folderId, String fileName, long size, FilePart filePart, ServerWebExchange exchange);
    Mono<Void> deleteFromSharedFolderPublic(String shareToken, String fileId, ServerWebExchange exchange);
    Mono<FileResponse> getSharedFileMetadataPublic(String shareToken, String fileId);
    Flux<byte[]> downloadFileFromSharedFolderPublic(String shareToken, String fileId, org.springframework.web.server.ServerWebExchange exchange);
    Mono<Boolean> isDescendant(UUID currentFolderId, UUID rootFolderId, Long userId);
    Mono<Long> getSharedFolderOwnerId(String shareToken);
}
