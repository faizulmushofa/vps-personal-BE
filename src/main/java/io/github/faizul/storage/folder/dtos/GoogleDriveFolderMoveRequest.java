package io.github.faizul.storage.folder.dtos;

public record GoogleDriveFolderMoveRequest(
    Long externalAccountId,
    String fileId,
    String targetFolderId
) {}
