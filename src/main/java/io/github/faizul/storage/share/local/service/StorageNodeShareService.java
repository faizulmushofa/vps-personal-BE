package io.github.faizul.storage.share.local.service;

import io.github.faizul.storage.share.service.ShareService;
import io.github.faizul.storage.file.dtos.ShareFileResponse;
import io.github.faizul.storage.file.dtos.ShareFileRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StorageNodeShareService extends ShareService {
    Mono<ShareFileResponse> shareFileWithLog(String fileId, ShareFileRequest request, org.springframework.web.server.ServerWebExchange exchange);
    Mono<Void> unshareFileWithLog(String fileId, Long targetUserId, org.springframework.web.server.ServerWebExchange exchange);
    Mono<Void> unshareFileByIdWithLog(Long shareId, org.springframework.web.server.ServerWebExchange exchange);
    Flux<byte[]> downloadPublicFileWithLog(String shareToken, org.springframework.web.server.ServerWebExchange exchange);
}
