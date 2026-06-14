package io.github.faizul.admin.dtos;

import java.util.List;

public record AdminUserResponse(
    Long id,
    String username,
    String email,
    String fullName,
    Long storageQuota,
    Long usedStorage,
    Boolean isActive,
    Integer aiDailyLimit,
    Integer dailyAiRequests,
    List<String> roles,
    Integer migrationDailyLimit,
    Long migrationMaxFileSize
) {}
