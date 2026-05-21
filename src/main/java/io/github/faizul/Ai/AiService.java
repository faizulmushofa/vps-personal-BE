package io.github.faizul.Ai;

import io.github.faizul.Ai.Dto.Request;
import io.github.faizul.Ai.Dto.Response;
import reactor.core.publisher.Mono;

public interface AiService {

    Mono<Response> summary(Request request);

}
