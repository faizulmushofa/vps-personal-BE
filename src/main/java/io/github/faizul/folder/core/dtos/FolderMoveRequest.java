package io.github.faizul.folder.core.dtos;

import java.util.UUID;

public record FolderMoveRequest(
    UUID sourceId,
    UUID targetFolderId,
    String type
) {}
