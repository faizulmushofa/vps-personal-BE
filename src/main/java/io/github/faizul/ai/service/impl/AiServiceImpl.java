package io.github.faizul.ai.service.impl;

import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.ai.dtos.AiRequest;
import io.github.faizul.ai.dtos.AiResponse;
import io.github.faizul.ai.service.AiQuotaAndLogService;
import io.github.faizul.ai.service.AiService;
import io.github.faizul.ai.service.cache.SummaryCacheService;
import io.github.faizul.ai.service.fallback.AiFallbackService;
import io.github.faizul.extraction.service.ExtractionService;
import io.github.faizul.infra.config.AiConfig;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.ai.service.AiConfigService;
import io.github.faizul.ai.dtos.AiSettings;
import io.github.faizul.storage.file.local.service.StorageNodeFileService;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;


@Service
@RequiredArgsConstructor
@Slf4j
public class AiServiceImpl implements AiService {

    private final AiFallbackService aiFallbackService;
    private final SummaryCacheService cacheService;
    private final ExtractionService pdfService;
    private final Scheduler aiScheduler;
    private final AiConfigService aiConfigService;
    private final AiQuotaAndLogService quotaAndLogService;
    private final CurrentUserContext currentUserContext;
    private final UserActivityService userActivityService;
    private final StorageNodeFileService storageNodeFileService;

    @Override
    public Mono<AiResponse> summary(AiRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> quotaAndLogService.checkAndIncrementQuota(userId)
                        .flatMap(user -> aiConfigService.getSummarySettings().flatMap(settings -> {
                            return aiFallbackService.callWithFallback(
                                    settings.primaryProvider(), settings.primaryModel(),
                                    settings.fallbackProvider(), settings.fallbackModel(),
                                    settings.fallback2Provider(), settings.fallback2Model(),
                                    settings.systemPrompt(), request.teks()
                            )
                            .flatMap(result -> quotaAndLogService.logTokenUsage(
                                    userId, "SUMMARY", settings.primaryProvider(), settings.primaryModel(), result)
                                    .onErrorResume(err -> {
                                        log.warn("Gagal mencatat penggunaan token AI untuk userId: {}", userId, err);
                                        return Mono.empty();
                                    })
                                    .then(Mono.defer(() -> userActivityService.log(userId, "AI_SUMMARY", "Melakukan rangkuman teks bebas", exchange)
                                            .onErrorResume(err -> {
                                                log.warn("Gagal mencatat aktivitas AI_SUMMARY untuk userId: {}", userId, err);
                                                return Mono.empty();
                                            })
                                    ))
                                    .thenReturn(result.content())
                            );
                        }))
                )
                .subscribeOn(aiScheduler)
                .map(AiResponse::new)
                .doOnError(e -> log.error("Gagal memproses summary", e));
    }

    @Override
    public Mono<AiResponse> summarizePdf(UUID fileId, org.springframework.web.server.ServerWebExchange exchange) {
        return cacheService.getCachedSummary(fileId)
                .flatMap(summary -> {
                    if (summary.equalsIgnoreCase("Maaf, input tidak dapat diproses.")) {
                        log.info("Terdeteksi summary tidak valid ('Maaf, input tidak dapat diproses.') untuk fileId: {}. Memicu pemrosesan ulang...", fileId);
                        return currentUserContext.getUserId()
                                .flatMap(userId -> userActivityService.log(userId, "AI_REPROCESS_SUMMARY", "Memproses ulang ringkasan PDF ID: " + fileId + " karena status gagal sebelumnya", exchange))
                                .onErrorResume(err -> Mono.empty())
                                .then(Mono.<String>empty());
                    }
                    return currentUserContext.getUserId()
                            .flatMap(userId -> userActivityService.log(userId, "AI_SUMMARY_PDF", "Mengambil ringkasan PDF ID: " + fileId + " dari cache", exchange))
                            .thenReturn(summary)
                            .onErrorResume(err -> Mono.just(summary))
                            .switchIfEmpty(Mono.just(summary));
                })
                .map(AiResponse::new)
                .switchIfEmpty(Mono.defer(() -> currentUserContext.getUserId()
                        .flatMap(userId -> quotaAndLogService.checkAndIncrementQuota(userId)
                                .flatMap(user -> pdfService.extractFile(fileId)
                                        .flatMap(text -> {
                                            String prompt = "Tolong rangkum teks dari dokumen berikut secara singkat, padat, terstruktur, dan jelas dalam Bahasa Indonesia. " +
                                                            "PENTING: Rangkum HANYA informasi yang benar-benar tertulis di dalam dokumen. Jangan berasumsi, menebak, berspekulasi, atau menambahkan informasi dari luar dokumen (hindari halusinasi). " +
                                                            "Jika teks dokumen tidak berisi informasi yang dapat dirangkum atau tidak terbaca, abaikan saja.\n\n" +
                                                            "Teks dokumen:\n" + text;
                                            return aiConfigService.getSummarySettings().flatMap(settings -> {
                                                return aiFallbackService.callWithFallback(
                                                        settings.primaryProvider(), settings.primaryModel(),
                                                        settings.fallbackProvider(), settings.fallbackModel(),
                                                        settings.fallback2Provider(), settings.fallback2Model(),
                                                        settings.systemPrompt(), prompt
                                                )
                                                .flatMap(result -> quotaAndLogService.logTokenUsage(
                                                        userId, "SUMMARY_PDF", settings.primaryProvider(), settings.primaryModel(), result)
                                                        .onErrorResume(err -> {
                                                            log.warn("Gagal mencatat penggunaan token AI untuk userId: {}", userId, err);
                                                            return Mono.empty();
                                                        })
                                                        .then(Mono.defer(() -> userActivityService.log(userId, "AI_SUMMARY_PDF", "Melakukan rangkuman berkas PDF ID: " + fileId, exchange)
                                                                .onErrorResume(err -> {
                                                                    log.warn("Gagal mencatat aktivitas AI_SUMMARY_PDF untuk userId: {}", userId, err);
                                                                    return Mono.empty();
                                                                })
                                                        ))
                                                        .thenReturn(result.content())
                                                );
                                            });
                                        })
                                )
                        )
                        .subscribeOn(aiScheduler)
                        .flatMap(summaryText -> cacheService.cacheSummary(fileId, summaryText))
                        .map(AiResponse::new)
                ))
                .doOnError(e -> log.error("Gagal memproses summary PDF untuk fileId: {}", fileId, e));
    }

    @Override
    public Mono<AiResponse> summarizePdf(String fileId, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> storageNodeFileService.resolveFileId(fileId, userId))
                .flatMap(uuid -> summarizePdf(uuid, exchange));
    }
}
