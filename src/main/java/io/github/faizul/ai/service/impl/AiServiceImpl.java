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
import io.github.faizul.setting.service.AppSettingService;
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
    private final AppSettingService appSettingService;
    private final AiQuotaAndLogService quotaAndLogService;
    private final CurrentUserContext currentUserContext;
    private final UserActivityService userActivityService;

    @Override
    public Mono<AiResponse> summary(AiRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> quotaAndLogService.checkAndIncrementQuota(userId)
                        .flatMap(user -> Mono.zip(
                                appSettingService.getSetting("ai.summary.primary.provider", AiConfig.SUMMARY_PRIMARY_PROVIDER),
                                appSettingService.getSetting("ai.summary.primary.model", AiConfig.SUMMARY_PRIMARY_MODEL),
                                appSettingService.getSetting("ai.summary.fallback.provider", AiConfig.SUMMARY_FALLBACK_PROVIDER),
                                appSettingService.getSetting("ai.summary.fallback.model", AiConfig.SUMMARY_FALLBACK_MODEL),
                                appSettingService.getSetting("ai.summary.fallback.provider.two", AiConfig.SUMMARY_FALLBACK_PROVIDER_TWO),
                                appSettingService.getSetting("ai.summary.fallback.model.two", AiConfig.SUMMARY_FALLBACK_MODEL_TWO),
                                appSettingService.getSetting("ai.summary.system_prompt", AiConfig.SUMMARY_SYSTEM_PROMPT)
                        ).flatMap(tuple -> {
                            String primaryProvider = tuple.getT1();
                            String primaryModel = tuple.getT2();
                            String fallback1Provider = tuple.getT3();
                            String fallback1Model = tuple.getT4();
                            String fallback2Provider = tuple.getT5();
                            String fallback2Model = tuple.getT6();
                            String systemPrompt = tuple.getT7();

                            return aiFallbackService.callWithFallback(
                                    primaryProvider, primaryModel,
                                    fallback1Provider, fallback1Model,
                                    fallback2Provider, fallback2Model,
                                    systemPrompt, request.teks()
                            )
                            .flatMap(result -> quotaAndLogService.logTokenUsage(
                                    userId, "SUMMARY", primaryProvider, primaryModel, result)
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
                                .then(Mono.empty());
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
                                            String prompt = "Tolong rangkum teks berikut secara singkat dan jelas dalam Bahasa Indonesia:\n\n" + text;
                                            return Mono.zip(
                                                    appSettingService.getSetting("ai.summary.primary.provider", AiConfig.SUMMARY_PRIMARY_PROVIDER),
                                                    appSettingService.getSetting("ai.summary.primary.model", AiConfig.SUMMARY_PRIMARY_MODEL),
                                                    appSettingService.getSetting("ai.summary.fallback.provider", AiConfig.SUMMARY_FALLBACK_PROVIDER),
                                                    appSettingService.getSetting("ai.summary.fallback.model", AiConfig.SUMMARY_FALLBACK_MODEL),
                                                    appSettingService.getSetting("ai.summary.fallback.provider.two", AiConfig.SUMMARY_FALLBACK_PROVIDER_TWO),
                                                    appSettingService.getSetting("ai.summary.fallback.model.two", AiConfig.SUMMARY_FALLBACK_MODEL_TWO),
                                                    appSettingService.getSetting("ai.summary.system_prompt", AiConfig.SUMMARY_SYSTEM_PROMPT)
                                            ).flatMap(tuple -> {
                                                String primaryProvider = tuple.getT1();
                                                String primaryModel = tuple.getT2();
                                                String fallback1Provider = tuple.getT3();
                                                String fallback1Model = tuple.getT4();
                                                String fallback2Provider = tuple.getT5();
                                                String fallback2Model = tuple.getT6();
                                                String systemPrompt = tuple.getT7();

                                                return aiFallbackService.callWithFallback(
                                                        primaryProvider, primaryModel,
                                                        fallback1Provider, fallback1Model,
                                                        fallback2Provider, fallback2Model,
                                                        systemPrompt, prompt
                                                )
                                                .flatMap(result -> quotaAndLogService.logTokenUsage(
                                                        userId, "SUMMARY_PDF", primaryProvider, primaryModel, result)
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
}
