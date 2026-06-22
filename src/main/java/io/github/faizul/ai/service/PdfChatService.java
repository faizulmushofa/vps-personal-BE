package io.github.faizul.ai.service;

import io.github.faizul.ai.dtos.AiRequest;
import io.github.faizul.ai.dtos.AiResponse;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface PdfChatService {
    Mono<AiResponse> chatPdf(UUID fileId, AiRequest request, org.springframework.web.server.ServerWebExchange exchange);
    Mono<AiResponse> chatPdf(String fileId, AiRequest request, org.springframework.web.server.ServerWebExchange exchange);
}
