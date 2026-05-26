package io.github.faizul.Ai;

import io.github.faizul.Ai.dtos.AiRequest;
import io.github.faizul.Ai.dtos.AiResponse;
import io.github.faizul.File.pdf.PdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GeminiService implements AiService {

    private final ChatClient chatClient;
    private final Scheduler aiScheduler;
    private final PdfService pdfService;

    @Override
    public Mono<AiResponse> summary(AiRequest request) {
        return Mono.fromCallable(() -> chatClient.prompt()
                .user(request.teks())
                .call()
                .content()
        )
        .subscribeOn(aiScheduler)
        .map(AiResponse::new)
        .onErrorResume(Exception.class, e -> {
            System.err.println("GAGAL menjalankan program: " + e.getMessage());
            e.printStackTrace();
            return Mono.just(new AiResponse("Gagal menghasilkan ringkasan karena masalah teknis."));
        });
    }

    @Override
    public Mono<AiResponse> summarizePdf(UUID fileId) {
        return pdfService.extractFile(fileId)
                .map(text -> "Tolong rangkum teks berikut secara singkat dan jelas dalam Bahasa Indonesia:\n\n" + text)
                .map(AiRequest::new)
                .flatMap(this::summary)
                .onErrorResume(Exception.class, e -> {
                    System.err.println("GAGAL merangkum PDF: " + e.getMessage());
                    e.printStackTrace();
                    return Mono.just(new AiResponse("Gagal memproses PDF: " + e.getMessage()));
                });
    }
}
