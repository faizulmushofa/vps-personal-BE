package io.github.faizul.storage.folder.dtos;

public record GoogleDriveItemResponse(
    String id,
    String name,
    Long size,
    String mimeType,
    String createdTime
) {}
