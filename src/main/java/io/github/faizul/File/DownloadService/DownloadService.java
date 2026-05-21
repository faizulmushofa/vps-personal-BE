package io.github.faizul.File.DownloadService;

import io.github.faizul.File.Dtos.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DownloadService {

    Mono<DownloadInitResponse> init(DownloadInitRequest request);


    Flux<byte[]> streamFile(UUID fileId);


    Flux<byte[]> streamFileChunked(UUID fileId, int chunkSize);


    Mono<DownloadStatusResponse> getStatus(UUID fileId);

    Mono<Void> cancel(UUID fileId);
}
