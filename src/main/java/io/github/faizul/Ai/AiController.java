package io.github.faizul.Ai;

import io.github.faizul.Ai.dtos.*;

import io.github.faizul.Ai.dtos.AiRequest;
import io.github.faizul.Ai.dtos.AiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/ai")
public class AiController {

    private final AiService aiService;

    @PostMapping("/summary")
    public Mono<ResponseEntity<AiResponse>> postString(@RequestBody AiRequest request) {
        return aiService.summary(request)
                .map(response -> ResponseEntity.ok().body(response));
    }
}
