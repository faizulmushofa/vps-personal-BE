package io.github.faizul.storage.file.dtos;

import java.time.Instant;
import java.util.UUID;

public record SharedByMeResponse(
    Long id,
    UUID fileId,
    String originalFileName,
    Long size,
    Instant createdAt,
    String provider,
    Boolean isPublic,
    String shareToken,
    String shareLink,
    Instant expiresAt,
    String sharedWithEmail
) {}
