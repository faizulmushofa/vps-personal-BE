package io.github.faizul.File.FileService;

import io.github.faizul.File.Dtos.FileResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FileService {
    Mono<FileResponse> findByUUID(UUID uuid);
    Mono<Void> deleteByUUID(UUID uuid);
    Flux<FileResponse> getAllByUserId();
    Flux<FileResponse> getAllForAdmin();
}
