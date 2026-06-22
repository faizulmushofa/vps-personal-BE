package io.github.faizul.ai.dtos;

public record AiSettings(
        String primaryProvider,
        String primaryModel,
        String fallbackProvider,
        String fallbackModel,
        String fallback2Provider,
        String fallback2Model,
        String systemPrompt
) {
}
