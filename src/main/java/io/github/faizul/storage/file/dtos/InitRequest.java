package io.github.faizul.storage.file.dtos;

import java.util.UUID;

public record InitRequest(
        String fileName,
        Long totalSize,
        String provider,
        Long externalAccountId,
        String folderId
) {
}

