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
    private final io.github.faizul.security.filter.CurrentUserContext currentUserContext;
    private final io.github.faizul.activity.UserActivityService userActivityService;

    @PostMapping("/summary")
    public Mono<ResponseEntity<AiResponse>> postString(@RequestBody AiRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> aiService.summary(request)
                        .flatMap(response -> userActivityService.log(userId, "AI_SUMMARY", "Melakukan rangkuman teks bebas", exchange)
                                .thenReturn(ResponseEntity.ok().body(response))
                        )
                );
    }

    @PostMapping("/summary/pdf/{fileId}")
    public Mono<ResponseEntity<AiResponse>> summarizePdf(@PathVariable UUID fileId, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> aiService.summarizePdf(fileId)
                        .flatMap(response -> userActivityService.log(userId, "AI_SUMMARY_PDF", "Melakukan rangkuman berkas PDF ID: " + fileId, exchange)
                                .thenReturn(ResponseEntity.ok().body(response))
                        )
                );
    }

    @PostMapping("/chat/pdf/{fileId}")
    public Mono<ResponseEntity<AiResponse>> chatPdf(@PathVariable UUID fileId, @RequestBody AiRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> pdfChatService.chatPdf(fileId, request)
                        .flatMap(response -> userActivityService.log(userId, "AI_CHAT_PDF", "Melakukan tanya-jawab asisten AI pada berkas PDF ID: " + fileId, exchange)
                                .thenReturn(ResponseEntity.ok().body(response))
                        )
                );
    }
}
