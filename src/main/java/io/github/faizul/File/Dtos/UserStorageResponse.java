package io.github.faizul.File.dtos;

public record UserStorageResponse(
    long usedBytes,
    long quotaBytes,
    boolean googleDriveConnected,
    Long googleUsedBytes,
    Long googleQuotaBytes
) {}
