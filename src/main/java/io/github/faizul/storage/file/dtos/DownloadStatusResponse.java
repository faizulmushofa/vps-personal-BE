package io.github.faizul.storage.file.dtos;

import io.github.faizul.storage.file.dtos.*;
import io.github.faizul.storage.file.model.*;

import io.github.faizul.storage.download.model.FileStatus;
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

