package io.github.faizul.File.Dtos;

import io.github.faizul.File.DownloadService.FileStatus;
import java.util.UUID;

public record DownloadStatusResponse(
    UUID sessionId,
    UUID fileId,
    FileStatus status,
    Long totalBytes,
    Long bytesSent,
    Double progress
) {
}

