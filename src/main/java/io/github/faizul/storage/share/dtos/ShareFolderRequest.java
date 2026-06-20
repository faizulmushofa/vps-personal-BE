package io.github.faizul.storage.share.dtos;

import java.time.Instant;

public record ShareFolderRequest(
    String folderId,
    String folderType,
    String permission, // "VIEW" or "EDIT"
    Boolean allowAnonymous,
    Instant expiresAt
) {}
