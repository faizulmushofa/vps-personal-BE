package io.github.faizul.folder.core.dtos;

public record GoogleDriveFolderCreateRequest(
    Long externalAccountId,
    String name,
    String parentId
) {}
