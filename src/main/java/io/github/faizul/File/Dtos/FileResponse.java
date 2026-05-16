package io.github.faizul.File.Dtos;

import java.time.Instant;
import java.util.UUID;

public record FileResponse(
        UUID id,
        String originalFileName,
        Long size,
        Instant createdAt
){}
