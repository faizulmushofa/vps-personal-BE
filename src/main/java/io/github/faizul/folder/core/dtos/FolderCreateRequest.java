package io.github.faizul.folder.core.dtos;

import java.util.UUID;

public record FolderCreateRequest(
    String name,
    UUID parentId
) {}
