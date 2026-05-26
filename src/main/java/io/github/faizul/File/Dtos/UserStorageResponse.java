package io.github.faizul.File.Dtos;

public record UserStorageResponse(
    long usedBytes,
    long quotaBytes
) {}
