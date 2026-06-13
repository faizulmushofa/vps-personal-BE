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
                                    String systemPrompt = "Anda adalah asisten AI yang menjawab pertanyaan pengguna berdasarkan dokumen PDF berikut. " +
                                            "Jawablah dengan sopan dan informatif berdasarkan isi dokumen ini:\n\n" + text;

                                    return Mono.zip(
                                            appSettingService.getSetting("ai.chat.primary.provider", AiConfig.CHAT_PRIMARY_PROVIDER),
                                            appSettingService.getSetting("ai.chat.primary.model", AiConfig.CHAT_PRIMARY_MODEL),
                                            appSettingService.getSetting("ai.chat.fallback.provider", AiConfig.CHAT_FALLBACK_PROVIDER),
                                            appSettingService.getSetting("ai.chat.fallback.model", AiConfig.CHAT_FALLBACK_MODEL)
                                    ).flatMap(tuple -> {
                                        String primaryProvider = tuple.getT1();
                                        String primaryModel = tuple.getT2();
                                        String fallbackProvider = tuple.getT3();
                                        String fallbackModel = tuple.getT4();

                                        return aiFallbackService.callWithFallback(
                                                primaryProvider, primaryModel,
                                                fallbackProvider, fallbackModel,
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
