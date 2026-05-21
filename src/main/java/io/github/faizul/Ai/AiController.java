package io.github.faizul.Ai;

import io.github.faizul.Ai.Dto.Request;
import io.github.faizul.Ai.Dto.Response;
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
    public Mono<ResponseEntity<Response>> postString(@RequestBody Request request) {
        return aiService.summary(request)
                .map(response -> ResponseEntity.ok().body(response));
    }
}
