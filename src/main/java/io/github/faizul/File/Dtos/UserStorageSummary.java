package io.github.faizul.File.Dtos;

public record UserStorageSummary(
    Long userId,
    String username,
    String email,
    long usedBytes,
    long quotaBytes
) {}
