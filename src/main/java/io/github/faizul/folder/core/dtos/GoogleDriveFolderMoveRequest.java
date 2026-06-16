package io.github.faizul.folder.core.dtos;

public record GoogleDriveFolderMoveRequest(
    Long externalAccountId,
    String fileId,
    String targetFolderId
) {}
