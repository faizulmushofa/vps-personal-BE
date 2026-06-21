package io.github.faizul.storage.share.dtos;

import java.time.Instant;

public record UpdateShareExpiryRequest(
    Instant expiresAt
) {}
