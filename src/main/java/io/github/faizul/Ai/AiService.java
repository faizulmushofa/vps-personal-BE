package io.github.faizul.Ai;

import io.github.faizul.Ai.dtos.AiRequest;
import io.github.faizul.Ai.dtos.AiResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AiService {

    Mono<AiResponse> summary(AiRequest request);

    Mono<AiResponse> summarizePdf(UUID fileId);

}
