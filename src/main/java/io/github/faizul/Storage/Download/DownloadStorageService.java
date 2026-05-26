package io.github.faizul.Storage.download;


import io.github.faizul.File.dtos.FileChunk;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface DownloadStorageService {

    Flux<FileChunk> downloadFile(Long userId, UUID id);

}
