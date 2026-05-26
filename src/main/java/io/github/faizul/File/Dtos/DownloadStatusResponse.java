package io.github.faizul.File.dtos;

import io.github.faizul.File.dtos.*;
import io.github.faizul.File.core.*;

import io.github.faizul.File.download.FileStatus;
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

