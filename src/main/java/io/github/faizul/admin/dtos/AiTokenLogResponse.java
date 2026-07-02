package io.github.faizul.admin.dtos;

import java.time.LocalDateTime;

public record AiTokenLogResponse(
    Long id,
    Long userId,
    String username,
    String email,
    String activityType,
    String provider,
    String modelName,
    Integer inputTokens,
    Integer outputTokens,
    Integer totalTokens,
    LocalDateTime createdAt
) {}
