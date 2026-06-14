package io.github.faizul.admin.dtos;

public record TokenHistoryEntry(
    String date,
    Long inputTokens,
    Long outputTokens,
    Long totalTokens
) {}
