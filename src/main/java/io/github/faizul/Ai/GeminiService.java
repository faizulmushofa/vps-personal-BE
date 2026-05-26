package io.github.faizul.Ai;

import io.github.faizul.Ai.dtos.*;

import io.github.faizul.Ai.dtos.AiRequest;
import io.github.faizul.Ai.dtos.AiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Service
@RequiredArgsConstructor
public class GeminiService implements AiService {

    private final ChatClient chatClient;
    private final Scheduler aiScheduler;

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

    private String processingPdf() {
        return "";
    }
}
