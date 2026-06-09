package io.github.faizul.File.download.googleDriveDownload;

import io.github.faizul.File.download.DownloadService;
import io.github.faizul.File.dtos.DownloadInitRequest;
import io.github.faizul.File.dtos.DownloadInitResponse;
import io.github.faizul.File.dtos.DownloadStatusResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class GoogleDriveServiceImp implements DownloadService {
    @Override
    public Mono<DownloadInitResponse> init(DownloadInitRequest request) {
        return null;
    }

    @Override
    public Flux<byte[]> streamFile(UUID fileId) {
        return null;
    }

    @Override
    public Flux<byte[]> streamFileChunked(UUID fileId, int chunkSize) {
        return null;
    }

    @Override
    public Mono<DownloadStatusResponse> getStatus(UUID fileId) {
        return null;
    }

    @Override
    public Mono<Void> cancel(UUID fileId) {
        return null;
    }
}
