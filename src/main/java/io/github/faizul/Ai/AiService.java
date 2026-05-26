package io.github.faizul.Ai;

import io.github.faizul.Ai.dtos.*;

import io.github.faizul.Ai.dtos.AiRequest;
import io.github.faizul.Ai.dtos.AiResponse;
import reactor.core.publisher.Mono;

public interface AiService {

    Mono<AiResponse> summary(AiRequest request);

}
