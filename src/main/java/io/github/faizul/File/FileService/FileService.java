package io.github.faizul.File.FileService;

import io.github.faizul.File.Dtos.FileResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FileService {
    Mono<FileResponse> findByUUID(UUID uuid);
}
