package io.github.faizul.storage.folder.dtos;

import java.util.UUID;

public record FolderCreateRequest(
    String name,
    UUID parentId
) {}
