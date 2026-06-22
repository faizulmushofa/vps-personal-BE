package io.github.faizul.ai.service;

import io.github.faizul.ai.dtos.AiSettings;
import reactor.core.publisher.Mono;

public interface AiConfigService {
    Mono<AiSettings> getSummarySettings();
    Mono<AiSettings> getChatSettings();
}
