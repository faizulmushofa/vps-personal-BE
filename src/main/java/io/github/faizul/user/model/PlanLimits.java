package io.github.faizul.user.model;

import lombok.Builder;

@Builder
public record PlanLimits(
    long storageQuota,
    int maxCloudAccounts,
    int aiDailyLimit,
    int migrationDailyLimit,
    long migrationMaxFileSize,
    int publicShareLimit,
    int privateShareLimit,
    int maxWorkspaces,
    int maxInputTokensPerRequest
) {}
