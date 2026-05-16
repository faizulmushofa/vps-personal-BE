package io.github.faizul.File.Dtos;

import io.github.faizul.File.UploadService.FileStatus;

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
