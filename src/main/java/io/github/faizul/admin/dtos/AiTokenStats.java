package io.github.faizul.admin.dtos;

import java.util.List;

public record AiTokenStats(
    Long todayInputTokens,
    Long todayOutputTokens,
    Long todayTotalTokens,
    Long monthInputTokens,
    Long monthOutputTokens,
    Long monthTotalTokens,
    List<TokenHistoryEntry> history
) {}
