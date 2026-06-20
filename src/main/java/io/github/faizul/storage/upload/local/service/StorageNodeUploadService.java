package io.github.faizul.storage.upload.local.service;

import io.github.faizul.storage.upload.service.UploadService;
import io.github.faizul.storage.file.dtos.InitRequest;
import io.github.faizul.storage.file.dtos.InitResponse;
import reactor.core.publisher.Mono;

public interface StorageNodeUploadService extends UploadService {
    Mono<InitResponse> createWithLog(InitRequest request, org.springframework.web.server.ServerWebExchange exchange);
    Mono<Void> cancelUploadWithLog(java.util.UUID fileId, org.springframework.web.server.ServerWebExchange exchange);
}
