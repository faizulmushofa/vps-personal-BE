package io.github.faizul.ai.service.client;

import reactor.core.publisher.Mono;

public interface AiClient {
    boolean supports(String provider);
    Mono<AiGenerationResult> generate(String systemPrompt, String userMessage, String model);
}
