package io.github.faizul.ai.service;

import io.github.faizul.ai.dtos.AiRequest;
import io.github.faizul.ai.dtos.AiResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AiService {

    Mono<AiResponse> summary(AiRequest request, org.springframework.web.server.ServerWebExchange exchange);

    Mono<AiResponse> summarizePdf(UUID fileId, org.springframework.web.server.ServerWebExchange exchange);

}
