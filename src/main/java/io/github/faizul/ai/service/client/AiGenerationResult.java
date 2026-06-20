package io.github.faizul.ai.service.client;

public record AiGenerationResult(
    String content,
    int promptTokens,
    int generationTokens
) {}
