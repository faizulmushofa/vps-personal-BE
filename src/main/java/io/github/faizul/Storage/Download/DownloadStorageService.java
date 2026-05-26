package io.github.faizul.Storage.Download;

import io.github.faizul.File.Dtos.FileChunk;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface DownloadStorageService {

    Flux<FileChunk> downloadFile(Long userId, UUID id);

}
