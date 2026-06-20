package io.github.faizul.storage.download.service;


import io.github.faizul.storage.file.dtos.FileChunk;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface DownloadStorageService {

    Flux<FileChunk> downloadFile(Long userId, UUID id);

}
