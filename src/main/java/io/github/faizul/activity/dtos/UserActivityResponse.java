package io.github.faizul.activity.dtos;

import java.time.LocalDateTime;

public record UserActivityResponse(
    Long id,
    Long userId,
    String username,
    String email,
    String activityType,
    String description,
    String ipAddress,
    LocalDateTime createdAt
) {}
