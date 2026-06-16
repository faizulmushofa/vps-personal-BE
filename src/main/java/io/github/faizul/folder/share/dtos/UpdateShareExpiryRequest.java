package io.github.faizul.folder.share.dtos;

import java.time.Instant;

public record UpdateShareExpiryRequest(
    Instant expiresAt
) {}
