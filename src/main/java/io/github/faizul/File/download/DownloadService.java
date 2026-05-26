package io.github.faizul.File.download;

import io.github.faizul.File.core.*;

import io.github.faizul.File.dtos.*;
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
