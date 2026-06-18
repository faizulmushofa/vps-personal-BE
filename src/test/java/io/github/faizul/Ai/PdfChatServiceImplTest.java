package io.github.faizul.Ai;

import io.github.faizul.Ai.cache.SummaryCacheService;
import io.github.faizul.Ai.dtos.AiRequest;
import io.github.faizul.Ai.dtos.AiResponse;
import io.github.faizul.Ai.fallback.AiFallbackService;
import io.github.faizul.setting.AppSettingService;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.User.core.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdfChatServiceImplTest {

    @Mock private AiFallbackService aiFallbackService;
    @Mock private SummaryCacheService cacheService;
    @Mock private AiService aiService;
    @Mock private AppSettingService appSettingService;
    @Mock private AiQuotaAndLogService quotaAndLogService;
    @Mock private CurrentUserContext currentUserContext;

    private PdfChatServiceImpl pdfChatService;

    @BeforeEach
    void setUp() {
        Scheduler testScheduler = Schedulers.immediate();
        pdfChatService = new PdfChatServiceImpl(
                aiFallbackService, cacheService, aiService, testScheduler,
                appSettingService, quotaAndLogService, currentUserContext
        );
    }

    @Test
    @DisplayName("should chat with PDF by retrieving context from cache and calling AI")
    void chatPdf_success() {
        UUID fileId = UUID.randomUUID();
        AiRequest request = new AiRequest("What is this document about?");
        User user = User.builder().id(1L).subscriptionTier("FREEMIUM").build();

        when(currentUserContext.getUserId()).thenReturn(Mono.just(1L));
        when(quotaAndLogService.checkAndIncrementQuota(1L)).thenReturn(Mono.just(user));
        when(cacheService.getCachedSummary(fileId)).thenReturn(Mono.just("This document describes cloud storage."));

        when(appSettingService.getSetting(eq("ai.chat.primary.provider"), any())).thenReturn(Mono.just("gemini"));
        when(appSettingService.getSetting(eq("ai.chat.primary.model"), any())).thenReturn(Mono.just("gemini-1.5-pro"));
        when(appSettingService.getSetting(eq("ai.chat.fallback.provider"), any())).thenReturn(Mono.just("groq"));
        when(appSettingService.getSetting(eq("ai.chat.fallback.model"), any())).thenReturn(Mono.just("llama3-8b"));
        when(appSettingService.getSetting(eq("ai.chat.fallback.provider.two"), any())).thenReturn(Mono.just("groq"));
        when(appSettingService.getSetting(eq("ai.chat.fallback.model.two"), any())).thenReturn(Mono.just("poolside/laguna-xs.2:free"));
        when(appSettingService.getSetting(eq("ai.chat.system_prompt"), any())).thenReturn(Mono.just("Anda adalah asisten AI..."));

        io.github.faizul.Ai.client.AiGenerationResult mockResult = new io.github.faizul.Ai.client.AiGenerationResult("This document is about cloud storage systems.", 10, 10);
        when(aiFallbackService.callWithFallback(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                contains("cloud storage"), eq("What is this document about?")))
                .thenReturn(Mono.just(mockResult));
        when(quotaAndLogService.logTokenUsage(eq(1L), eq("CHAT"), anyString(), anyString(), eq(mockResult)))
                .thenReturn(Mono.empty());

        StepVerifier.create(pdfChatService.chatPdf(fileId, request))
                .assertNext(response -> assertThat(response.response())
                        .isEqualTo("This document is about cloud storage systems."))
                .verifyComplete();
    }

    @Test
    @DisplayName("should trigger reprocessing summary when cached summary is empty or invalid during chat")
    void chatPdf_reprocessInvalidCache() {
        UUID fileId = UUID.randomUUID();
        AiRequest request = new AiRequest("What is this document about?");
        User user = User.builder().id(1L).subscriptionTier("FREEMIUM").build();

        when(currentUserContext.getUserId()).thenReturn(Mono.just(1L));
        when(quotaAndLogService.checkAndIncrementQuota(1L)).thenReturn(Mono.just(user));
        when(cacheService.getCachedSummary(fileId)).thenReturn(Mono.just("Maaf, input tidak dapat diproses."));
        when(aiService.summarizePdf(fileId)).thenReturn(Mono.just(new AiResponse("Reprocessed cloud storage summary context")));

        when(appSettingService.getSetting(eq("ai.chat.primary.provider"), any())).thenReturn(Mono.just("gemini"));
        when(appSettingService.getSetting(eq("ai.chat.primary.model"), any())).thenReturn(Mono.just("gemini-1.5-pro"));
        when(appSettingService.getSetting(eq("ai.chat.fallback.provider"), any())).thenReturn(Mono.just("groq"));
        when(appSettingService.getSetting(eq("ai.chat.fallback.model"), any())).thenReturn(Mono.just("llama3-8b"));
        when(appSettingService.getSetting(eq("ai.chat.fallback.provider.two"), any())).thenReturn(Mono.just("groq"));
        when(appSettingService.getSetting(eq("ai.chat.fallback.model.two"), any())).thenReturn(Mono.just("poolside/laguna-xs.2:free"));
        when(appSettingService.getSetting(eq("ai.chat.system_prompt"), any())).thenReturn(Mono.just("Anda adalah asisten AI..."));

        io.github.faizul.Ai.client.AiGenerationResult mockResult = new io.github.faizul.Ai.client.AiGenerationResult("This document is about cloud storage.", 10, 10);
        when(aiFallbackService.callWithFallback(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                contains("Reprocessed cloud storage"), eq("What is this document about?")))
                .thenReturn(Mono.just(mockResult));
        when(quotaAndLogService.logTokenUsage(eq(1L), eq("CHAT"), anyString(), anyString(), eq(mockResult)))
                .thenReturn(Mono.empty());

        StepVerifier.create(pdfChatService.chatPdf(fileId, request))
                .assertNext(response -> assertThat(response.response())
                        .isEqualTo("This document is about cloud storage."))
                .verifyComplete();

        verify(aiService).summarizePdf(fileId);
    }

    @Test
    @DisplayName("should return error message when summary generation/reprocessing fails during chat")
    void chatPdf_reprocessError() {
        UUID fileId = UUID.randomUUID();
        AiRequest request = new AiRequest("What is this about?");
        User user = User.builder().id(1L).subscriptionTier("FREEMIUM").build();

        when(currentUserContext.getUserId()).thenReturn(Mono.just(1L));
        when(quotaAndLogService.checkAndIncrementQuota(1L)).thenReturn(Mono.just(user));
        when(cacheService.getCachedSummary(fileId)).thenReturn(Mono.empty());
        when(aiService.summarizePdf(fileId)).thenReturn(Mono.error(new RuntimeException("AI service down")));

        StepVerifier.create(pdfChatService.chatPdf(fileId, request))
                .expectErrorMatches(t -> t.getMessage().contains("AI service down"))
                .verify();
    }

    @Test
    @DisplayName("should return error message when AI call fails")
    void chatPdf_aiError() {
        UUID fileId = UUID.randomUUID();
        AiRequest request = new AiRequest("Summarize");
        User user = User.builder().id(1L).subscriptionTier("FREEMIUM").build();

        when(currentUserContext.getUserId()).thenReturn(Mono.just(1L));
        when(quotaAndLogService.checkAndIncrementQuota(1L)).thenReturn(Mono.just(user));
        when(cacheService.getCachedSummary(fileId)).thenReturn(Mono.just("Some text"));

        when(appSettingService.getSetting(eq("ai.chat.primary.provider"), any())).thenReturn(Mono.just("gemini"));
        when(appSettingService.getSetting(eq("ai.chat.primary.model"), any())).thenReturn(Mono.just("gemini-1.5-pro"));
        when(appSettingService.getSetting(eq("ai.chat.fallback.provider"), any())).thenReturn(Mono.just("groq"));
        when(appSettingService.getSetting(eq("ai.chat.fallback.model"), any())).thenReturn(Mono.just("llama3-8b"));
        when(appSettingService.getSetting(eq("ai.chat.fallback.provider.two"), any())).thenReturn(Mono.just("groq"));
        when(appSettingService.getSetting(eq("ai.chat.fallback.model.two"), any())).thenReturn(Mono.just("poolside/laguna-xs.2:free"));
        when(appSettingService.getSetting(eq("ai.chat.system_prompt"), any())).thenReturn(Mono.just("Anda adalah asisten AI yang menjawab..."));

        when(aiFallbackService.callWithFallback(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("AI timeout")));

        StepVerifier.create(pdfChatService.chatPdf(fileId, request))
                .expectErrorMatches(t -> t.getMessage().contains("AI timeout"))
                .verify();
    }
}
