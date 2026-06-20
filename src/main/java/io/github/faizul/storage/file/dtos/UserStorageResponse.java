package io.github.faizul.storage.file.dtos;

public record UserStorageResponse(
    long usedBytes,
    long quotaBytes,
    boolean googleDriveConnected,
    Long googleUsedBytes,
    Long googleQuotaBytes
) {}
