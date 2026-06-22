package io.github.faizul.ai.service.impl;

import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.ai.dtos.AiRequest;
import io.github.faizul.ai.service.AiQuotaAndLogService;
import io.github.faizul.ai.service.cache.SummaryCacheService;
import io.github.faizul.ai.service.client.AiGenerationResult;
import io.github.faizul.ai.service.fallback.AiFallbackService;
import io.github.faizul.extraction.service.ExtractionService;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.ai.service.AiConfigService;
import io.github.faizul.ai.dtos.AiSettings;
import io.github.faizul.storage.file.local.service.StorageNodeFileService;
import io.github.faizul.storage.file.model.File;
import io.github.faizul.user.model.User;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
class AiServiceImplTest {

    @Mock private AiFallbackService aiFallbackService;
    @Mock private SummaryCacheService cacheService;
    @Mock private ExtractionService pdfService;
    @Mock private AiConfigService aiConfigService;
    @Mock private AiQuotaAndLogService quotaAndLogService;
    @Mock private CurrentUserContext currentUserContext;
    @Mock private UserActivityService userActivityService;
    @Mock private StorageNodeFileService storageNodeFileService;

    private AiServiceImpl aiService;

    @BeforeEach
    void setUp() {
        Scheduler testScheduler = Schedulers.immediate();
        aiService = new AiServiceImpl(
                aiFallbackService, cacheService, pdfService, testScheduler,
                aiConfigService, quotaAndLogService, currentUserContext, userActivityService,
                storageNodeFileService
        );

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setSubscriptionTier("FREEMIUM");

        AiSettings settings = new AiSettings("gemini", "gemini-1.5-flash", "groq", "llama3-8b", "groq", "llama3-70b", "System prompt");

        lenient().when(currentUserContext.getUserId()).thenReturn(Mono.just(1L));
        lenient().when(quotaAndLogService.checkAndIncrementQuota(anyLong())).thenReturn(Mono.just(mockUser));
        lenient().when(aiConfigService.getSummarySettings()).thenReturn(Mono.just(settings));
        lenient().when(quotaAndLogService.logTokenUsage(anyLong(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());
        lenient().when(userActivityService.log(anyLong(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("summary")
    class SummaryTests {

        @Test
        @DisplayName("should return AI summary for given text")
        void summary_success() {
            AiRequest request = new AiRequest("Some text to summarize");

            when(aiFallbackService.callWithFallback(
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), eq("Some text to summarize")))
                    .thenReturn(Mono.just(new AiGenerationResult("This is a summary", 10, 10)));

            StepVerifier.create(aiService.summary(request, null))
                    .assertNext(response -> assertThat(response.response()).isEqualTo("This is a summary"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should propagate error when AI fails")
        void summary_error() {
            AiRequest request = new AiRequest("Some text");

            when(aiFallbackService.callWithFallback(
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.error(new RuntimeException("AI service down")));

            StepVerifier.create(aiService.summary(request, null))
                    .expectErrorMatches(t -> t.getMessage().contains("AI service down"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("summarizePdf")
    class SummarizePdfTests {

        @Test
        @DisplayName("should return cached summary if available")
        void summarizePdf_cached() {
            UUID fileId = UUID.randomUUID();

            when(cacheService.getCachedSummary(fileId)).thenReturn(Mono.just("Cached summary"));

            StepVerifier.create(aiService.summarizePdf(fileId, null))
                    .assertNext(response -> assertThat(response.response()).isEqualTo("Cached summary"))
                    .verifyComplete();

            verify(pdfService, never()).extractFile(any());
            verify(aiFallbackService, never()).callWithFallback(
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should extract PDF and generate summary when not cached")
        void summarizePdf_notCached() {
            UUID fileId = UUID.randomUUID();

            when(cacheService.getCachedSummary(fileId)).thenReturn(Mono.empty());
            when(pdfService.extractFile(fileId)).thenReturn(Mono.just("PDF text content"));
            when(aiFallbackService.callWithFallback(
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), contains("PDF text content")))
                    .thenReturn(Mono.just(new AiGenerationResult("Generated summary", 10, 10)));
            when(cacheService.cacheSummary(fileId, "Generated summary"))
                    .thenReturn(Mono.just("Generated summary"));

            StepVerifier.create(aiService.summarizePdf(fileId, null))
                    .assertNext(response -> assertThat(response.response()).isEqualTo("Generated summary"))
                    .verifyComplete();

            verify(cacheService).cacheSummary(fileId, "Generated summary");
        }

        @Test
        @DisplayName("should reprocess summary when cached summary is 'Maaf, input tidak dapat diproses.'")
        void summarizePdf_reprocessInvalidCache() {
            UUID fileId = UUID.randomUUID();

            when(cacheService.getCachedSummary(fileId)).thenReturn(Mono.just("Maaf, input tidak dapat diproses."));
            when(pdfService.extractFile(fileId)).thenReturn(Mono.just("PDF text content"));
            when(aiFallbackService.callWithFallback(
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), contains("PDF text content")))
                    .thenReturn(Mono.just(new AiGenerationResult("Generated summary", 10, 10)));
            when(cacheService.cacheSummary(fileId, "Generated summary"))
                    .thenReturn(Mono.just("Generated summary"));
            when(userActivityService.log(anyLong(), eq("AI_REPROCESS_SUMMARY"), anyString(), any()))
                    .thenReturn(Mono.empty());

            StepVerifier.create(aiService.summarizePdf(fileId, null))
                    .assertNext(response -> assertThat(response.response()).isEqualTo("Generated summary"))
                    .verifyComplete();

            verify(userActivityService).log(anyLong(), eq("AI_REPROCESS_SUMMARY"), anyString(), any());
            verify(cacheService).cacheSummary(fileId, "Generated summary");
        }

        @Test
        @DisplayName("should propagate error when PDF extraction fails")
        void summarizePdf_extractionError() {
            UUID fileId = UUID.randomUUID();

            when(cacheService.getCachedSummary(fileId)).thenReturn(Mono.empty());
            when(pdfService.extractFile(fileId))
                    .thenReturn(Mono.error(new RuntimeException("File not found")));

            StepVerifier.create(aiService.summarizePdf(fileId, null))
                    .expectErrorMatches(t -> t.getMessage().contains("File not found"))
                    .verify();
        }

        @Test
        @DisplayName("should resolve file ID and summarize pdf")
        void summarizePdf_byStringId() {
            String fileId = "some-file-id";
            UUID resolvedUuid = UUID.randomUUID();

            when(storageNodeFileService.resolveFileId(fileId, 1L)).thenReturn(Mono.just(resolvedUuid));
            when(cacheService.getCachedSummary(resolvedUuid)).thenReturn(Mono.just("Resolved summary"));

            StepVerifier.create(aiService.summarizePdf(fileId, null))
                    .assertNext(response -> assertThat(response.response()).isEqualTo("Resolved summary"))
                    .verifyComplete();

            verify(storageNodeFileService).resolveFileId(fileId, 1L);
        }
    }
}
