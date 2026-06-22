package io.github.faizul.ai.service.impl;

import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.ai.dtos.AiRequest;
import io.github.faizul.ai.dtos.AiResponse;
import io.github.faizul.ai.model.Summary;
import io.github.faizul.ai.service.AiQuotaAndLogService;
import io.github.faizul.ai.service.AiService;
import io.github.faizul.ai.service.PdfChatService;
import io.github.faizul.ai.service.cache.SummaryCacheService;
import io.github.faizul.ai.service.fallback.AiFallbackService;
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
public class PdfChatServiceImpl implements PdfChatService {

    private final AiFallbackService aiFallbackService;
    private final SummaryCacheService cacheService;
    private final AiService aiService;
    private final Scheduler aiScheduler;
    private final AiConfigService aiConfigService;
    private final AiQuotaAndLogService quotaAndLogService;
    private final CurrentUserContext currentUserContext;
    private final UserActivityService userActivityService;
    private final StorageNodeFileService storageNodeFileService;

    @Override
    public Mono<AiResponse> chatPdf(UUID fileId, AiRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> quotaAndLogService.checkAndIncrementQuota(userId)
                        .flatMap(user -> {
                            Mono<String> contextSummaryMono = cacheService.getCachedSummary(fileId)
                                    .filter(summary -> !summary.equalsIgnoreCase("Maaf, input tidak dapat diproses."))
                                    .switchIfEmpty(Mono.defer(() -> {
                                        log.info("Summary kosong atau tidak valid saat chat. Memicu pembentukan/pemrosesan ulang summary untuk fileId: {}", fileId);
                                        return aiService.summarizePdf(fileId, exchange)
                                                .map(AiResponse::response);
                                    }));

                            return contextSummaryMono.flatMap(text -> {
                                return aiConfigService.getChatSettings().flatMap(settings -> {
                                    String systemPrompt = settings.systemPrompt() + "\n\nRingkasan Dokumen:\n" + text;

                                    return aiFallbackService.callWithFallback(
                                            settings.primaryProvider(), settings.primaryModel(),
                                            settings.fallbackProvider(), settings.fallbackModel(),
                                            settings.fallback2Provider(), settings.fallback2Model(),
                                            systemPrompt, request.teks()
                                    )
                                    .flatMap(result -> quotaAndLogService.logTokenUsage(
                                            userId, "CHAT", settings.primaryProvider(), settings.primaryModel(), result)
                                            .onErrorResume(err -> {
                                                log.warn("Gagal mencatat penggunaan token AI untuk userId: {}", userId, err);
                                                return Mono.empty();
                                            })
                                            .then(Mono.defer(() -> userActivityService.log(userId, "AI_CHAT_PDF", "Melakukan tanya-jawab asisten AI pada berkas PDF ID: " + fileId, exchange)
                                                    .onErrorResume(err -> {
                                                        log.warn("Gagal mencatat aktivitas AI_CHAT_PDF untuk userId: {}", userId, err);
                                                        return Mono.empty();
                                                     })
                                            ))
                                            .thenReturn(result.content())
                                    );
                                });
                            });
                        })
                )
                .subscribeOn(aiScheduler)
                .map(AiResponse::new)
                .doOnError(e -> log.error("Gagal melakukan chat PDF untuk fileId: {}", fileId, e));
    }

    @Override
    public Mono<AiResponse> chatPdf(String fileId, AiRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> storageNodeFileService.resolveFileId(fileId, userId))
                .flatMap(uuid -> chatPdf(uuid, request, exchange));
    }
}
