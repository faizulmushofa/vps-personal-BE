package io.github.faizul.ai.controller;

import io.github.faizul.ai.dtos.AiRequest;
import io.github.faizul.ai.dtos.AiResponse;
import io.github.faizul.ai.service.AiService;
import io.github.faizul.ai.service.PdfChatService;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.storage.file.local.service.StorageNodeFileService;
import java.util.UUID;
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
    private final StorageNodeFileService fileService;
    private final CurrentUserContext currentUserContext;

    private Mono<UUID> resolveFileId(String fileIdString) {
        return currentUserContext.getUserId()
                .flatMap(userId -> fileService.resolveFileId(fileIdString, userId));
    }

    @PostMapping("/summary")
    public Mono<ResponseEntity<AiResponse>> postString(@RequestBody AiRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return aiService.summary(request, exchange)
                .map(response -> ResponseEntity.ok().body(response));
    }

    @PostMapping("/summary/pdf/{fileId}")
    public Mono<ResponseEntity<AiResponse>> summarizePdf(@PathVariable String fileId, org.springframework.web.server.ServerWebExchange exchange) {
        return resolveFileId(fileId)
                .flatMap(uuid -> aiService.summarizePdf(uuid, exchange))
                .map(response -> ResponseEntity.ok().body(response));
    }

    @PostMapping("/chat/pdf/{fileId}")
    public Mono<ResponseEntity<AiResponse>> chatPdf(@PathVariable String fileId, @RequestBody AiRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return resolveFileId(fileId)
                .flatMap(uuid -> pdfChatService.chatPdf(uuid, request, exchange))
                .map(response -> ResponseEntity.ok().body(response));
    }
}
