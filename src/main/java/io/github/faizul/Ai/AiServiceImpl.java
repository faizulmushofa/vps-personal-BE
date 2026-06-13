package io.github.faizul.Ai;

import io.github.faizul.Ai.cache.SummaryCacheService;
import io.github.faizul.Ai.dtos.AiRequest;
import io.github.faizul.Ai.dtos.AiResponse;
import io.github.faizul.Ai.fallback.AiFallbackService;
import io.github.faizul.File.pdf.PdfService;
import io.github.faizul.infra.config.AiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiServiceImpl implements AiService {

    private final AiFallbackService aiFallbackService;
    private final SummaryCacheService cacheService;
    private final PdfService pdfService;
    private final Scheduler aiScheduler;

    @Override
    public Mono<AiResponse> summary(AiRequest request) {
        return aiFallbackService.callWithFallback(
                AiConfig.SUMMARY_PRIMARY_PROVIDER, AiConfig.SUMMARY_PRIMARY_MODEL,
                AiConfig.SUMMARY_FALLBACK_PROVIDER, AiConfig.SUMMARY_FALLBACK_MODEL,
                AiConfig.SYSTEM_PROMPT, request.teks()
        )
                .subscribeOn(aiScheduler)
                .map(AiResponse::new)
                .doOnError(e -> log.error("Gagal memproses summary", e));
    }

    @Override
    public Mono<AiResponse> summarizePdf(UUID fileId) {
        return cacheService.getCachedSummary(fileId)
                .map(AiResponse::new)
                .switchIfEmpty(Mono.defer(() -> pdfService.extractFile(fileId)
                        .flatMap(text -> {
                            String prompt = "Tolong rangkum teks berikut secara singkat dan jelas dalam Bahasa Indonesia:\n\n" + text;
                            return aiFallbackService.callWithFallback(
                                    AiConfig.SUMMARY_PRIMARY_PROVIDER, AiConfig.SUMMARY_PRIMARY_MODEL,
                                    AiConfig.SUMMARY_FALLBACK_PROVIDER, AiConfig.SUMMARY_FALLBACK_MODEL,
                                    AiConfig.SYSTEM_PROMPT, prompt
                            );
                        })
                        .subscribeOn(aiScheduler)
                        .flatMap(summaryText -> cacheService.cacheSummary(fileId, summaryText))
                        .map(AiResponse::new)
                ))
                .doOnError(e -> log.error("Gagal memproses summary PDF untuk fileId: {}", fileId, e));
    }
}
