package io.github.faizul.Ai;

import io.github.faizul.Ai.Dto.Request;
import io.github.faizul.Ai.Dto.Response;
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
    public Mono<Response> summary(Request request) {
        return Mono.fromCallable(() -> chatClient.prompt()
                .user(request.teks())
                .call()
                .content()
        )
        .subscribeOn(aiScheduler)
        .map(Response::new)
        .onErrorResume(Exception.class, e -> {
            System.err.println("GAGAL menjalankan program: " + e.getMessage());
            e.printStackTrace();
            return Mono.just(new Response("Gagal menghasilkan ringkasan karena masalah teknis."));
        });
    }

    private String processingPdf() {
        return "";
    }
}
