package io.github.faizul.User.core;

import lombok.Builder;

@Builder
public record PlanLimits(
    long storageQuota,
    int maxCloudAccounts,
    int aiDailyLimit,
    int migrationDailyLimit,
    long migrationMaxFileSize,
    int publicShareLimit,
    int privateShareLimit
) {}
