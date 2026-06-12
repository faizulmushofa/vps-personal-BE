package io.github.faizul.Ai.fallback;

import io.github.faizul.infra.config.AiConfig;
import io.github.faizul.Ai.client.AiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiFallbackService {

    private final List<AiClient> aiClients;

    public Mono<String> callWithFallback(
            String primaryProvider, String primaryModel,
            String fallbackProvider, String fallbackModel,
            String systemPrompt, String userMessage) {

        AiClient primary = getClient(primaryProvider);
        AiClient fallback = getClient(fallbackProvider);

        log.info("Mengirim permintaan AI dengan Primary: {} ({}) dan Fallback: {} ({})", 
                primaryProvider, primaryModel, fallbackProvider, fallbackModel);

        return primary.generate(systemPrompt, userMessage, primaryModel)
                .timeout(Duration.ofSeconds(AiConfig.PRIMARY_TIMEOUT_SECONDS))
                .onErrorResume(e -> {
                    log.warn("Model utama ({}) gagal/timeout, beralih ke fallback ({}). Error: {}", 
                            primaryModel, fallbackModel, e.getMessage());
                    return fallback.generate(systemPrompt, userMessage, fallbackModel)
                            .timeout(Duration.ofSeconds(AiConfig.FALLBACK_TIMEOUT_SECONDS))
                            .doOnError(err -> log.error("Model fallback ({}) juga gagal/timeout. Error: {}", 
                                    fallbackModel, err.getMessage()));
                });
    }

    private AiClient getClient(String provider) {
        return aiClients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported AI provider: " + provider));
    }
}
