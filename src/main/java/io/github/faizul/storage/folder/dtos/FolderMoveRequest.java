package io.github.faizul.storage.folder.dtos;

import java.util.UUID;

public record FolderMoveRequest(
    UUID sourceId,
    UUID targetFolderId,
    String type
) {}
