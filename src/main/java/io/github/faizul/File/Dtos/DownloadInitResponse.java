package io.github.faizul.File.dtos;

import io.github.faizul.File.dtos.*;
import io.github.faizul.File.core.*;

import io.github.faizul.File.download.FileStatus;
import java.util.UUID;

public record DownloadInitResponse(
    UUID sessionId,
    UUID fileId,
    String fileName,
    Long size,
    FileStatus status
) {
}

