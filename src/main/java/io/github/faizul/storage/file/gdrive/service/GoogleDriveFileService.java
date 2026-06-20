package io.github.faizul.storage.file.gdrive.service;

import io.github.faizul.storage.file.dtos.FileResponse;
import io.github.faizul.storage.file.dtos.UserStorageResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface GoogleDriveFileService {
    Flux<FileResponse> getFiles(Long externalAccountId);
    Mono<String> deleteFile(String id, org.springframework.web.server.ServerWebExchange exchange);
    Mono<UserStorageResponse> getStorage(Long externalAccountId);
    Mono<Void> syncGoogleDrive(Long externalAccountId, org.springframework.web.server.ServerWebExchange exchange);
}
