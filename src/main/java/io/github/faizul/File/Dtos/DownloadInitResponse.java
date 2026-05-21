package io.github.faizul.File.Dtos;

import io.github.faizul.File.DownloadService.FileStatus;
import java.util.UUID;

public record DownloadInitResponse(
    UUID sessionId,
    UUID fileId,
    String fileName,
    Long size,
    FileStatus status
) {
}

