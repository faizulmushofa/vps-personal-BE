package io.github.faizul.ai.controller;

import io.github.faizul.ai.dtos.AiRequest;
import io.github.faizul.ai.dtos.AiResponse;
import io.github.faizul.ai.service.AiService;
import io.github.faizul.ai.service.PdfChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/ai")
public class AiController {

    private final AiService aiService;
    private final PdfChatService pdfChatService;

    @PostMapping("/summary")
    public Mono<ResponseEntity<AiResponse>> postString(@RequestBody AiRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return aiService.summary(request, exchange)
                .map(response -> ResponseEntity.ok().body(response));
    }

    @PostMapping("/summary/pdf/{fileId}")
    public Mono<ResponseEntity<AiResponse>> summarizePdf(@PathVariable String fileId, org.springframework.web.server.ServerWebExchange exchange) {
        return aiService.summarizePdf(fileId, exchange)
                .map(response -> ResponseEntity.ok().body(response));
    }

    @PostMapping("/chat/pdf/{fileId}")
    public Mono<ResponseEntity<AiResponse>> chatPdf(@PathVariable String fileId, @RequestBody AiRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return pdfChatService.chatPdf(fileId, request, exchange)
                .map(response -> ResponseEntity.ok().body(response));
    }
}
