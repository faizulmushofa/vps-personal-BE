package io.github.faizul.File.dtos;

import java.time.Instant;

public record ShareFileResponse(
    Long id,
    String email,
    Boolean isPublic,
    String shareToken,
    String shareLink,
    Instant expiresAt
) {}
