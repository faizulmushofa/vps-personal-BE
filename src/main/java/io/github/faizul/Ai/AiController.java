package io.github.faizul.Ai;

import io.github.faizul.Ai.dtos.AiRequest;
import io.github.faizul.Ai.dtos.AiResponse;
import io.github.faizul.File.core.FileRepository;
import io.github.faizul.File.core.File;
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
    private final FileRepository fileRepository;

    private Mono<UUID> resolveFileId(String fileIdString) {
        try {
            return Mono.just(UUID.fromString(fileIdString));
        } catch (IllegalArgumentException e) {
            return fileRepository.findByStorageNameAndProvider(fileIdString, "GOOGLE_DRIVE")
                    .map(File::getId)
                    .switchIfEmpty(Mono.error(new java.util.NoSuchElementException("Berkas tidak ditemukan!")));
        }
    }

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
    public Mono<ResponseEntity<AiResponse>> summarizePdf(@PathVariable String fileId, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> resolveFileId(fileId)
                        .flatMap(uuid -> aiService.summarizePdf(uuid)
                                .flatMap(response -> userActivityService.log(userId, "AI_SUMMARY_PDF", "Melakukan rangkuman berkas PDF ID: " + fileId, exchange)
                                        .thenReturn(ResponseEntity.ok().body(response))
                                )
                        )
                );
    }

    @PostMapping("/chat/pdf/{fileId}")
    public Mono<ResponseEntity<AiResponse>> chatPdf(@PathVariable String fileId, @RequestBody AiRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> resolveFileId(fileId)
                        .flatMap(uuid -> pdfChatService.chatPdf(uuid, request)
                                .flatMap(response -> userActivityService.log(userId, "AI_CHAT_PDF", "Melakukan tanya-jawab asisten AI pada berkas PDF ID: " + fileId, exchange)
                                        .thenReturn(ResponseEntity.ok().body(response))
                                )
                        )
                );
    }
}
