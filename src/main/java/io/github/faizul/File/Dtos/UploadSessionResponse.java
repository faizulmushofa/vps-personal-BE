package io.github.faizul.File.dtos;

import io.github.faizul.File.dtos.*;
import io.github.faizul.File.core.*;

import io.github.faizul.File.upload.FileStatus;

import java.time.Instant;
import java.util.UUID;

public record UploadSessionResponse(
        UUID sessionId,
        UUID fileId,
        Integer uploadedChunks,
        Integer totalChunks,
        FileStatus status,
        Instant createdAt,
        Instant completedAt
){}
