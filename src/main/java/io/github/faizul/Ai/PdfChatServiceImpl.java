package io.github.faizul.Ai;

import io.github.faizul.Ai.dtos.AiRequest;
import io.github.faizul.Ai.dtos.AiResponse;
import io.github.faizul.Ai.fallback.AiFallbackService;
import io.github.faizul.File.pdf.PdfService;
import io.github.faizul.infra.config.AiConfig;
import io.github.faizul.setting.AppSettingService;
import io.github.faizul.security.filter.CurrentUserContext;
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
public class PdfChatServiceImpl implements PdfChatService {

    private final AiFallbackService aiFallbackService;
    private final PdfService pdfService;
    private final Scheduler aiScheduler;
    private final AppSettingService appSettingService;
    private final AiQuotaAndLogService quotaAndLogService;
    private final CurrentUserContext currentUserContext;

    @Override
    public Mono<AiResponse> chatPdf(UUID fileId, AiRequest request) {
        return currentUserContext.getUserId()
                .flatMap(userId -> quotaAndLogService.checkAndIncrementQuota(userId)
                        .flatMap(user -> pdfService.extractFile(fileId)
                                .flatMap(text -> {
                                    return Mono.zip(
                                            appSettingService.getSetting("ai.chat.primary.provider", AiConfig.CHAT_PRIMARY_PROVIDER),
                                            appSettingService.getSetting("ai.chat.primary.model", AiConfig.CHAT_PRIMARY_MODEL),
                                            appSettingService.getSetting("ai.chat.fallback.provider", AiConfig.CHAT_FALLBACK_PROVIDER),
                                            appSettingService.getSetting("ai.chat.fallback.model", AiConfig.CHAT_FALLBACK_MODEL),
                                            appSettingService.getSetting("ai.chat.fallback.provider.two", AiConfig.CHAT_FALLBACK_PROVIDER_TWO),
                                            appSettingService.getSetting("ai.chat.fallback.model.two", AiConfig.CHAT_FALLBACK_MODEL_TWO),
                                            appSettingService.getSetting("ai.chat.system_prompt", AiConfig.CHAT_SYSTEM_PROMPT)
                                    ).flatMap(tuple -> {
                                        String primaryProvider = tuple.getT1();
                                        String primaryModel = tuple.getT2();
                                        String fallback1Provider = tuple.getT3();
                                        String fallback1Model = tuple.getT4();
                                        String fallback2Provider = tuple.getT5();
                                        String fallback2Model = tuple.getT6();
                                        String chatSystemPrompt = tuple.getT7();

                                        String systemPrompt = chatSystemPrompt + "\n\nDokumen:\n" + text;

                                        return aiFallbackService.callWithFallback(
                                                primaryProvider, primaryModel,
                                                fallback1Provider, fallback1Model,
                                                fallback2Provider, fallback2Model,
                                                systemPrompt, request.teks()
                                        )
                                        .flatMap(result -> quotaAndLogService.logTokenUsage(
                                                userId, "CHAT", primaryProvider, primaryModel, result)
                                                .thenReturn(result.content())
                                        );
                                    });
                                })
                        )
                )
                .subscribeOn(aiScheduler)
                .map(AiResponse::new)
                .doOnError(e -> log.error("Gagal melakukan chat PDF untuk fileId: {}", fileId, e));
    }
}
