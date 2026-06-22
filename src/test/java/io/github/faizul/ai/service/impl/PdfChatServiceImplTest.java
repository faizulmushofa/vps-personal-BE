package io.github.faizul.ai.service.impl;

import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.ai.dtos.AiRequest;
import io.github.faizul.ai.dtos.AiResponse;
import io.github.faizul.ai.service.AiQuotaAndLogService;
import io.github.faizul.ai.service.AiService;
import io.github.faizul.ai.service.cache.SummaryCacheService;
import io.github.faizul.ai.service.client.AiGenerationResult;
import io.github.faizul.ai.service.fallback.AiFallbackService;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.ai.service.AiConfigService;
import io.github.faizul.ai.dtos.AiSettings;
import io.github.faizul.storage.file.local.service.StorageNodeFileService;
import io.github.faizul.user.model.User;
import java.util.UUID;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;




@ExtendWith(MockitoExtension.class)
class PdfChatServiceImplTest {

    @Mock private AiFallbackService aiFallbackService;
    @Mock private SummaryCacheService cacheService;
    @Mock private AiService aiService;
    @Mock private AiConfigService aiConfigService;
    @Mock private AiQuotaAndLogService quotaAndLogService;
    @Mock private CurrentUserContext currentUserContext;
    @Mock private UserActivityService userActivityService;
    @Mock private StorageNodeFileService storageNodeFileService;

    private PdfChatServiceImpl pdfChatService;

    @BeforeEach
    void setUp() {
        Scheduler testScheduler = Schedulers.immediate();
        pdfChatService = new PdfChatServiceImpl(
                aiFallbackService, cacheService, aiService, testScheduler,
                aiConfigService, quotaAndLogService, currentUserContext,
                userActivityService, storageNodeFileService
        );
    }

    @Test
    @DisplayName("should chat with PDF by retrieving context from cache and calling AI")
    void chatPdf_success() {
        UUID fileId = UUID.randomUUID();
        AiRequest request = new AiRequest("What is this document about?");
        User user = User.builder().id(1L).subscriptionTier("FREEMIUM").build();
        AiSettings settings = new AiSettings("gemini", "gemini-1.5-pro", "groq", "llama3-8b", "groq", "poolside/laguna-xs.2:free", "Anda adalah asisten AI...");

        when(currentUserContext.getUserId()).thenReturn(Mono.just(1L));
        when(quotaAndLogService.checkAndIncrementQuota(1L)).thenReturn(Mono.just(user));
        when(cacheService.getCachedSummary(fileId)).thenReturn(Mono.just("This document describes cloud storage."));
        when(aiConfigService.getChatSettings()).thenReturn(Mono.just(settings));

        AiGenerationResult mockResult = new AiGenerationResult("This document is about cloud storage systems.", 10, 10);
        when(aiFallbackService.callWithFallback(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                contains("cloud storage"), eq("What is this document about?")))
                .thenReturn(Mono.just(mockResult));
        when(quotaAndLogService.logTokenUsage(eq(1L), eq("CHAT"), anyString(), anyString(), eq(mockResult)))
                .thenReturn(Mono.empty());
        when(userActivityService.log(any(), any(), any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(pdfChatService.chatPdf(fileId, request, null))
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
        AiSettings settings = new AiSettings("gemini", "gemini-1.5-pro", "groq", "llama3-8b", "groq", "poolside/laguna-xs.2:free", "Anda adalah asisten AI...");

        when(currentUserContext.getUserId()).thenReturn(Mono.just(1L));
        when(quotaAndLogService.checkAndIncrementQuota(1L)).thenReturn(Mono.just(user));
        when(cacheService.getCachedSummary(fileId)).thenReturn(Mono.just("Maaf, input tidak dapat diproses."));
        when(aiService.summarizePdf(fileId, null)).thenReturn(Mono.just(new AiResponse("Reprocessed cloud storage summary context")));
        when(aiConfigService.getChatSettings()).thenReturn(Mono.just(settings));

        AiGenerationResult mockResult = new AiGenerationResult("This document is about cloud storage.", 10, 10);
        when(aiFallbackService.callWithFallback(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                contains("Reprocessed cloud storage"), eq("What is this document about?")))
                .thenReturn(Mono.just(mockResult));
        when(quotaAndLogService.logTokenUsage(eq(1L), eq("CHAT"), anyString(), anyString(), eq(mockResult)))
                .thenReturn(Mono.empty());
        when(userActivityService.log(any(), any(), any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(pdfChatService.chatPdf(fileId, request, null))
                .assertNext(response -> assertThat(response.response())
                        .isEqualTo("This document is about cloud storage."))
                .verifyComplete();

        verify(aiService).summarizePdf(fileId, null);
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
        when(aiService.summarizePdf(fileId, null)).thenReturn(Mono.error(new RuntimeException("AI service down")));

        StepVerifier.create(pdfChatService.chatPdf(fileId, request, null))
                .expectErrorMatches(t -> t.getMessage().contains("AI service down"))
                .verify();
    }

    @Test
    @DisplayName("should return error message when AI call fails")
    void chatPdf_aiError() {
        UUID fileId = UUID.randomUUID();
        AiRequest request = new AiRequest("Summarize");
        User user = User.builder().id(1L).subscriptionTier("FREEMIUM").build();
        AiSettings settings = new AiSettings("gemini", "gemini-1.5-pro", "groq", "llama3-8b", "groq", "poolside/laguna-xs.2:free", "Anda adalah asisten AI...");

        when(currentUserContext.getUserId()).thenReturn(Mono.just(1L));
        when(quotaAndLogService.checkAndIncrementQuota(1L)).thenReturn(Mono.just(user));
        when(cacheService.getCachedSummary(fileId)).thenReturn(Mono.just("Some text"));
        when(aiConfigService.getChatSettings()).thenReturn(Mono.just(settings));

        when(aiFallbackService.callWithFallback(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("AI timeout")));

        StepVerifier.create(pdfChatService.chatPdf(fileId, request, null))
                .expectErrorMatches(t -> t.getMessage().contains("AI timeout"))
                .verify();
    }

    @Test
    @DisplayName("should resolve file ID and chat with PDF by String ID")
    void chatPdf_byStringId() {
        String fileId = "some-file-id";
        UUID resolvedUuid = UUID.randomUUID();
        AiRequest request = new AiRequest("What is this document about?");
        User user = User.builder().id(1L).subscriptionTier("FREEMIUM").build();
        AiSettings settings = new AiSettings("gemini", "gemini-1.5-pro", "groq", "llama3-8b", "groq", "poolside/laguna-xs.2:free", "Anda adalah asisten AI...");

        when(currentUserContext.getUserId()).thenReturn(Mono.just(1L));
        when(storageNodeFileService.resolveFileId(fileId, 1L)).thenReturn(Mono.just(resolvedUuid));
        when(quotaAndLogService.checkAndIncrementQuota(1L)).thenReturn(Mono.just(user));
        when(cacheService.getCachedSummary(resolvedUuid)).thenReturn(Mono.just("This document describes cloud storage."));
        when(aiConfigService.getChatSettings()).thenReturn(Mono.just(settings));

        AiGenerationResult mockResult = new AiGenerationResult("This document is about cloud storage systems.", 10, 10);
        when(aiFallbackService.callWithFallback(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                contains("cloud storage"), eq("What is this document about?")))
                .thenReturn(Mono.just(mockResult));
        when(quotaAndLogService.logTokenUsage(eq(1L), eq("CHAT"), anyString(), anyString(), eq(mockResult)))
                .thenReturn(Mono.empty());
        when(userActivityService.log(any(), any(), any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(pdfChatService.chatPdf(fileId, request, null))
                .assertNext(response -> assertThat(response.response())
                        .isEqualTo("This document is about cloud storage systems."))
                .verifyComplete();

        verify(storageNodeFileService).resolveFileId(fileId, 1L);
    }
}
