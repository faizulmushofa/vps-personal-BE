package io.github.faizul.Ai;

import io.github.faizul.Ai.dtos.AiRequest;
import io.github.faizul.Ai.dtos.AiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/ai")
public class AiController {

    private final AiService aiService;
    private final PdfChatService pdfChatService;

    @PostMapping("/summary")
    public Mono<ResponseEntity<AiResponse>> postString(@RequestBody AiRequest request) {
        return aiService.summary(request)
                .map(response -> ResponseEntity.ok().body(response));
    }

    @PostMapping("/summary/pdf/{fileId}")
    public Mono<ResponseEntity<AiResponse>> summarizePdf(@PathVariable UUID fileId) {
        return aiService.summarizePdf(fileId)
                .map(response -> ResponseEntity.ok().body(response));
    }

    @PostMapping("/chat/pdf/{fileId}")
    public Mono<ResponseEntity<AiResponse>> chatPdf(@PathVariable UUID fileId, @RequestBody AiRequest request) {
        return pdfChatService.chatPdf(fileId, request)
                .map(response -> ResponseEntity.ok().body(response));
    }
}
