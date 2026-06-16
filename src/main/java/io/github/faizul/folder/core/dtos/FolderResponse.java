package io.github.faizul.folder.core.dtos;

import java.time.Instant;
import java.util.UUID;

public record FolderResponse(
    UUID id,
    String name,
    UUID parentId,
    Long userId,
    Instant createdAt
) {}
