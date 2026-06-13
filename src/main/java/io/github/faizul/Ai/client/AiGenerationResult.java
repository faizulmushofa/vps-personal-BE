package io.github.faizul.Ai.client;

public record AiGenerationResult(
    String content,
    int promptTokens,
    int generationTokens
) {}
