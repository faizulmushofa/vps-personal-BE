package io.github.faizul.File.dtos;

public record ShareFileRequest(
    String email,
    Boolean isPublic,
    Integer expiresInDays,
    Integer expiresInHours
) {}
