package io.github.faizul.ai.service.client;

import io.github.faizul.infra.config.AiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service("groqService")
@Slf4j
public class GroqService implements AiClient {

    private final ChatClient chatClient;

    public GroqService(@Qualifier("openAiChatModel") ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public boolean supports(String provider) {
        return "groq".equalsIgnoreCase(provider) || "openrouter".equalsIgnoreCase(provider);
    }

    @Override
    public Mono<AiGenerationResult> generate(String systemPrompt, String userMessage, String model) {
        return Mono.fromCallable(() -> {
            log.info("Memanggil Groq/OpenRouter model: {}, temp: {}", model, AiConfig.DEFAULT_TEMPERATURE);
            try {
                var chatResponse = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userMessage)
                        .options(OpenAiChatOptions.builder()
                                .model(model)
                                .temperature(AiConfig.DEFAULT_TEMPERATURE))
                        .call()
                        .chatResponse();
                
                String content = chatResponse.getResult().getOutput().getText();
                var usage = chatResponse.getMetadata().getUsage();
                int promptTokens = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0;
                int generationTokens = usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens().intValue() : 0;

                if (promptTokens == 0) {
                    promptTokens = Math.max(1, (systemPrompt.length() + userMessage.length()) / 4);
                }
                if (generationTokens == 0 && content != null) {
                    generationTokens = Math.max(1, content.length() / 4);
                }

                log.info("Groq/OpenRouter sukses menghasilkan konten. Token: Input={}, Output={}", promptTokens, generationTokens);
                return new AiGenerationResult(content, promptTokens, generationTokens);
            } catch (Exception e) {
                if (isCancellation(e)) {
                    log.warn("Groq/OpenRouter call cancelled or interrupted for model: {}", model);
                } else {
                    log.error("Groq/OpenRouter gagal memanggil API untuk model {}: {}", model, e.getMessage());
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
