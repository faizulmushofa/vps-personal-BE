package io.github.faizul.storage.file.dtos;

public record ShareFileRequest(
    String email,
    Boolean isPublic,
    Integer expiresInDays,
    Integer expiresInHours
) {}
