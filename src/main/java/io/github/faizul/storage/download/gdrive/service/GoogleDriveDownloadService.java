package io.github.faizul.storage.download.gdrive.service;

import io.github.faizul.storage.download.service.DownloadService;
import io.github.faizul.storage.file.dtos.FileResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

public interface GoogleDriveDownloadService extends DownloadService {
    Mono<UUID> resolveFileId(String fileIdString, Long userId);
    Mono<Map<String, Object>> getExternalFileMetadata(Long externalAccountId, String fileId, Long userId);
    Flux<byte[]> downloadExternalFile(Long externalAccountId, String fileId, Long userId, org.springframework.web.server.ServerWebExchange exchange);
    Flux<byte[]> downloadFileWithLog(UUID fileId, Long userId, org.springframework.web.server.ServerWebExchange exchange);
    Mono<FileResponse> getFileDetails(UUID fileId);
}
