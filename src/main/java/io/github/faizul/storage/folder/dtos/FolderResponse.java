package io.github.faizul.storage.folder.dtos;

import java.time.Instant;
import java.util.UUID;

public record FolderResponse(
    String id,
    String name,
    String parentId,
    Long userId,
    Instant createdAt
) {
    public FolderResponse(UUID id, String name, UUID parentId, Long userId, Instant createdAt) {
        this(id != null ? id.toString() : null, name, parentId != null ? parentId.toString() : null, userId, createdAt);
    }
}
