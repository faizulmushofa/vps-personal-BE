package io.github.faizul.Ai.cache;

import io.github.faizul.Ai.Summary;
import io.github.faizul.Ai.SummaryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SummaryCacheServiceTest {

    @Mock private SummaryRepository summaryRepository;

    @InjectMocks
    private SummaryCacheService summaryCacheService;

    @Nested
    @DisplayName("getCachedSummary")
    class GetCachedSummaryTests {

        @Test
        @DisplayName("should return cached summary when it exists")
        void getCachedSummary_found() {
            UUID fileId = UUID.randomUUID();
            Summary summary = Summary.builder()
                    .id(1L).fileId(fileId).summary("Cached text").build();

            when(summaryRepository.findByFileId(fileId)).thenReturn(Mono.just(summary));

            StepVerifier.create(summaryCacheService.getCachedSummary(fileId))
                    .assertNext(text -> assertThat(text).isEqualTo("Cached text"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should return empty when no cache exists")
        void getCachedSummary_notFound() {
            UUID fileId = UUID.randomUUID();
            when(summaryRepository.findByFileId(fileId)).thenReturn(Mono.empty());

            StepVerifier.create(summaryCacheService.getCachedSummary(fileId))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("cacheSummary")
    class CacheSummaryTests {

        @Test
        @DisplayName("should save and return summary text when not existing")
        void cacheSummary_success() {
            UUID fileId = UUID.randomUUID();
            Summary saved = Summary.builder()
                    .id(1L).fileId(fileId).summary("New summary").build();

            when(summaryRepository.findByFileId(fileId)).thenReturn(Mono.empty());
            when(summaryRepository.save(any(Summary.class))).thenReturn(Mono.just(saved));

            StepVerifier.create(summaryCacheService.cacheSummary(fileId, "New summary"))
                    .assertNext(text -> assertThat(text).isEqualTo("New summary"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should update and return summary text when already exists")
        void cacheSummary_update() {
            UUID fileId = UUID.randomUUID();
            Summary existing = Summary.builder()
                    .id(1L).fileId(fileId).summary("Old summary").build();
            Summary updated = Summary.builder()
                    .id(1L).fileId(fileId).summary("New summary").build();

            when(summaryRepository.findByFileId(fileId)).thenReturn(Mono.just(existing));
            when(summaryRepository.save(any(Summary.class))).thenReturn(Mono.just(updated));

            StepVerifier.create(summaryCacheService.cacheSummary(fileId, "New summary"))
                    .assertNext(text -> assertThat(text).isEqualTo("New summary"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should handle duplicate by fetching existing cache")
        void cacheSummary_duplicateKey() {
            UUID fileId = UUID.randomUUID();
            Summary existing = Summary.builder()
                    .id(1L).fileId(fileId).summary("Existing summary").build();

            when(summaryRepository.findByFileId(fileId))
                    .thenReturn(Mono.empty())
                    .thenReturn(Mono.just(existing));
            when(summaryRepository.save(any(Summary.class)))
                    .thenReturn(Mono.error(new DuplicateKeyException("Duplicate")));

            StepVerifier.create(summaryCacheService.cacheSummary(fileId, "New summary"))
                    .assertNext(text -> assertThat(text).isEqualTo("Existing summary"))
                    .verifyComplete();
        }
    }
}
