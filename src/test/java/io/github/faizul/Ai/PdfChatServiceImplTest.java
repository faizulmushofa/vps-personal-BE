package io.github.faizul.Ai;

import io.github.faizul.Ai.dtos.AiRequest;
import io.github.faizul.Ai.dtos.AiResponse;
import io.github.faizul.Ai.fallback.AiFallbackService;
import io.github.faizul.File.pdf.PdfService;
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
    @Mock private PdfService pdfService;

    private PdfChatServiceImpl pdfChatService;

    @BeforeEach
    void setUp() {
        Scheduler testScheduler = Schedulers.immediate();
        pdfChatService = new PdfChatServiceImpl(aiFallbackService, pdfService, testScheduler);
    }

    @Test
    @DisplayName("should chat with PDF by extracting text and calling AI")
    void chatPdf_success() {
        UUID fileId = UUID.randomUUID();
        AiRequest request = new AiRequest("What is this document about?");

        when(pdfService.extractFile(fileId)).thenReturn(Mono.just("This document describes cloud storage."));
        when(aiFallbackService.callWithFallback(
                anyString(), anyString(), anyString(), anyString(),
                contains("cloud storage"), eq("What is this document about?")))
                .thenReturn(Mono.just("This document is about cloud storage systems."));

        StepVerifier.create(pdfChatService.chatPdf(fileId, request))
                .assertNext(response -> assertThat(response.response())
                        .isEqualTo("This document is about cloud storage systems."))
                .verifyComplete();
    }

    @Test
    @DisplayName("should return error message when PDF extraction fails")
    void chatPdf_extractionError() {
        UUID fileId = UUID.randomUUID();
        AiRequest request = new AiRequest("What is this about?");

        when(pdfService.extractFile(fileId))
                .thenReturn(Mono.error(new RuntimeException("Cannot access file")));

        StepVerifier.create(pdfChatService.chatPdf(fileId, request))
                .assertNext(response -> assertThat(response.response())
                        .contains("Gagal melakukan chat PDF"))
                .verifyComplete();
    }

    @Test
    @DisplayName("should return error message when AI call fails")
    void chatPdf_aiError() {
        UUID fileId = UUID.randomUUID();
        AiRequest request = new AiRequest("Summarize");

        when(pdfService.extractFile(fileId)).thenReturn(Mono.just("Some text"));
        when(aiFallbackService.callWithFallback(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("AI timeout")));

        StepVerifier.create(pdfChatService.chatPdf(fileId, request))
                .assertNext(response -> assertThat(response.response())
                        .contains("Gagal melakukan chat PDF"))
                .verifyComplete();
    }
}
