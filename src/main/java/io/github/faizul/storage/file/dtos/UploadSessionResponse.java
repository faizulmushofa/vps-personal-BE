package io.github.faizul.storage.file.dtos;

import io.github.faizul.storage.upload.model.FileStatus;

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
