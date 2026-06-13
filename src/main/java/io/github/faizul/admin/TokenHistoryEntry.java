package io.github.faizul.admin;

public record TokenHistoryEntry(
    String date,
    Long inputTokens,
    Long outputTokens,
    Long totalTokens
) {}
