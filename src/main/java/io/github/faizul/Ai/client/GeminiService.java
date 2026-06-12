package io.github.faizul.Ai.client;

import io.github.faizul.infra.config.AiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service("geminiService")
@Slf4j
public class GeminiService implements AiClient {

    private final ChatClient chatClient;

    public GeminiService(@Qualifier("googleGenAiChatModel") ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public boolean supports(String provider) {
        return "gemini".equalsIgnoreCase(provider);
    }

    @Override
    public Mono<String> generate(String systemPrompt, String userMessage, String model) {
        return Mono.fromCallable(() -> {
            log.info("Memanggil Gemini Native model: {}, temp: {}", model, AiConfig.DEFAULT_TEMPERATURE);
            try {
                String content = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userMessage)
                        .options(GoogleGenAiChatOptions.builder()
                                .model(model)
                                .temperature(AiConfig.DEFAULT_TEMPERATURE))
                        .call()
                        .content();
                log.info("Gemini Native sukses menghasilkan konten");
                return content;
            } catch (Exception e) {
                if (isCancellation(e)) {
                    log.warn("Gemini Native call cancelled or interrupted for model: {}", model);
                } else {
                    log.error("Gemini Native gagal memanggil API untuk model {}: {}", model, e.getMessage(), e);
                }
                throw e;
            }
        });
    }

    private static boolean isCancellation(Throwable t) {
        if (t == null) return false;
        if (t instanceof InterruptedException ||
            t instanceof java.io.InterruptedIOException ||
            t instanceof java.util.concurrent.CancellationException) {
            return true;
        }
        String msg = t.getMessage();
        if (msg != null && (
            msg.contains("interrupted") || 
            msg.contains("InterruptedException") || 
            msg.contains("InterruptedIOException") || 
            msg.contains("cancel")
        )) {
            return true;
        }
        if (t.getCause() != null && isCancellation(t.getCause())) {
            return true;
        }
        for (Throwable suppressed : t.getSuppressed()) {
            if (isCancellation(suppressed)) {
                return true;
            }
        }
        return false;
    }
}
