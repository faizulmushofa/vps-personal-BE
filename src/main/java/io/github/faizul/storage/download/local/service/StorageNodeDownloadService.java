package io.github.faizul.storage.download.local.service;

import io.github.faizul.storage.download.service.DownloadService;
import io.github.faizul.storage.file.dtos.FileResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface StorageNodeDownloadService extends DownloadService {
    Mono<FileResponse> getFileDetails(UUID fileId);
    Flux<byte[]> downloadFileWithLog(UUID fileId, Long userId, org.springframework.web.server.ServerWebExchange exchange);
}
