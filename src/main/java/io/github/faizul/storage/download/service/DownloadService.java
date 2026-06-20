package io.github.faizul.storage.download.service;

import io.github.faizul.storage.file.dtos.*;
import io.github.faizul.storage.file.dtos.DownloadInitRequest;
import io.github.faizul.storage.file.dtos.DownloadInitResponse;
import io.github.faizul.storage.file.dtos.DownloadStatusResponse;
import io.github.faizul.storage.file.model.*;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;




public interface DownloadService {

    Mono<DownloadInitResponse> init(DownloadInitRequest request);

    Flux<byte[]> streamFile(UUID fileId);

    Flux<byte[]> streamFileChunked(UUID fileId, int chunkSize);

    Mono<DownloadStatusResponse> getStatus(UUID fileId);

    Mono<Void> cancel(UUID fileId);
}
