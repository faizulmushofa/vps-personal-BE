package io.github.faizul.folder.share.dtos;

import java.time.Instant;

public record SharedFolderResponse(
    Long id,
    String folderId,
    String folderType,
    String shareToken,
    String permission,
    Boolean allowAnonymous,
    Instant expiresAt,
    Instant createdAt,
    String folderName
) {}
