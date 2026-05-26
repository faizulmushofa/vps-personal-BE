package io.github.faizul.File.dtos;

import io.github.faizul.File.dtos.*;
import io.github.faizul.File.core.*;

import java.time.Instant;
import java.util.UUID;

public record FileResponse(
        UUID id,
        String originalFileName,
        Long size,
        Instant createdAt
){}
