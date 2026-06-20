package io.github.faizul.storage.folder.dtos;

public record GoogleDriveFolderCreateRequest(
    Long externalAccountId,
    String name,
    String parentId
) {}
