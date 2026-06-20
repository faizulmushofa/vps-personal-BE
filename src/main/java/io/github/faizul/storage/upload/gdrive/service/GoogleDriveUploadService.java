package io.github.faizul.storage.upload.gdrive.service;

import io.github.faizul.storage.upload.service.UploadService;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GoogleDriveUploadService extends UploadService {
    Mono<Void> handleChunkUpload(UUID fileId, int chunkIndex, FilePart filePart);
}
