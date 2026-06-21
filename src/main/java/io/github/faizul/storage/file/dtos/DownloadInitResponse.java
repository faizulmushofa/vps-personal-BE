package io.github.faizul.storage.file.dtos;

import io.github.faizul.storage.download.model.FileStatus;
import java.util.UUID;

public record DownloadInitResponse(
    UUID sessionId,
    UUID fileId,
    String fileName,
    Long size,
    FileStatus status
) {
}

