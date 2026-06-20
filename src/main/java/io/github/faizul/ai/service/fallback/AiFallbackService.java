package io.github.faizul.ai.service.fallback;

import io.github.faizul.infra.config.AiConfig;
import io.github.faizul.ai.service.client.AiClient;
import io.github.faizul.ai.service.client.AiGenerationResult;
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

    public Mono<AiGenerationResult> callWithFallback(
            String primaryProvider, String primaryModel,
            String fallback1Provider, String fallback1Model,
            String fallback2Provider, String fallback2Model,
            String systemPrompt, String userMessage) {

        return Mono.defer(() -> {
            try {
                AiClient primary = getClient(primaryProvider);
                AiClient fallback1 = getClient(fallback1Provider);
                AiClient fallback2 = getClient(fallback2Provider);

                log.info("Mengirim permintaan AI dengan Primary: {} ({}), Fallback 1: {} ({}), Fallback 2: {} ({})", 
                        primaryProvider, primaryModel, fallback1Provider, fallback1Model, fallback2Provider, fallback2Model);

                return Mono.delay(Duration.ofSeconds(1)) // Delay 1 detik untuk menghindari tabrakan RPM
                        .then(primary.generate(systemPrompt, userMessage, primaryModel))
                        .timeout(Duration.ofSeconds(AiConfig.PRIMARY_TIMEOUT_SECONDS))
                        .onErrorResume(e1 -> {
                            log.warn("Model utama ({}) gagal/timeout, beralih ke Fallback 1 ({}). Error: {}", 
                                    primaryModel, fallback1Model, e1.getMessage());
                            return Mono.delay(Duration.ofSeconds(1)) // Delay 1 detik sebelum fallback 1
                                    .then(fallback1.generate(systemPrompt, userMessage, fallback1Model))
                                    .timeout(Duration.ofSeconds(AiConfig.FALLBACK_TIMEOUT_SECONDS))
                                    .onErrorResume(e2 -> {
                                        log.warn("Fallback 1 ({}) gagal/timeout, beralih ke Fallback 2 ({}). Error: {}", 
                                                fallback1Model, fallback2Model, e2.getMessage());
                                        return Mono.delay(Duration.ofSeconds(1)) // Delay 1 detik sebelum fallback 2
                                                .then(fallback2.generate(systemPrompt, userMessage, fallback2Model))
                                                .timeout(Duration.ofSeconds(AiConfig.FALLBACK_TIMEOUT_SECONDS))
                                                .doOnError(err -> log.error("Fallback 2 ({}) juga gagal/timeout. Error: {}", 
                                                        fallback2Model, err.getMessage()));
                                    });
                        });
            } catch (Exception e) {
                return Mono.error(e);
            }
        });
    }

    private AiClient getClient(String provider) {
        return aiClients.stream()
                .filter(client -> client.supports(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported AI provider: " + provider));
    }
}
