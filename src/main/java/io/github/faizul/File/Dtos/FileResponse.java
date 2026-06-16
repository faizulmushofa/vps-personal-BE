package io.github.faizul.File.dtos;

import io.github.faizul.File.dtos.*;
import io.github.faizul.File.core.*;

import java.time.Instant;
import java.util.UUID;

public record FileResponse(
        String id,
        String originalFileName,
        Long size,
        Instant createdAt,
        String provider,
        Long externalAccountId,
        String ownerEmail,
        Instant expiresAt
){
    public FileResponse(String id, String originalFileName, Long size, Instant createdAt, String provider, Long externalAccountId, String ownerEmail) {
        this(id, originalFileName, size, createdAt, provider, externalAccountId, ownerEmail, null);
    }

    public FileResponse(UUID id, String originalFileName, Long size, Instant createdAt, String provider, Long externalAccountId, String ownerEmail) {
        this(id != null ? id.toString() : null, originalFileName, size, createdAt, provider, externalAccountId, ownerEmail, null);
    }
}
