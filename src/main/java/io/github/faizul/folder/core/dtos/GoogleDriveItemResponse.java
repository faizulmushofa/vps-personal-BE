package io.github.faizul.folder.core.dtos;

public record GoogleDriveItemResponse(
    String id,
    String name,
    Long size,
    String mimeType,
    String createdTime
) {}
