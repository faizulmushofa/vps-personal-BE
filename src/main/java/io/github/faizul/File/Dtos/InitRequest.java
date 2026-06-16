package io.github.faizul.File.dtos;

import java.util.UUID;

public record InitRequest(
        String fileName,
        Long totalSize,
        String provider,
        Long externalAccountId,
        String folderId
) {
}

