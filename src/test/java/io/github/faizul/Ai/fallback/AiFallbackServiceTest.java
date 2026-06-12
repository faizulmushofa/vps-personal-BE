package io.github.faizul.Ai.fallback;

import io.github.faizul.Ai.client.AiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiFallbackServiceTest {

    private AiClient primaryClient;
    private AiClient fallbackClient;
    private AiFallbackService aiFallbackService;

    @BeforeEach
    void setUp() {
        primaryClient = mock(AiClient.class);
        fallbackClient = mock(AiClient.class);

        lenient().when(primaryClient.supports("groq")).thenReturn(true);
        lenient().when(primaryClient.supports("gemini")).thenReturn(false);
        lenient().when(primaryClient.supports(anyString())).thenReturn(false);
        lenient().when(fallbackClient.supports("gemini")).thenReturn(true);
        lenient().when(fallbackClient.supports("groq")).thenReturn(false);
        lenient().when(fallbackClient.supports(anyString())).thenReturn(false);

        aiFallbackService = new AiFallbackService(List.of(primaryClient, fallbackClient));
    }

    @Test
    @DisplayName("should use primary provider when it succeeds")
    void callWithFallback_primarySuccess() {
        when(primaryClient.generate(anyString(), anyString(), eq("groq-model")))
                .thenReturn(Mono.just("Primary response"));

        StepVerifier.create(aiFallbackService.callWithFallback(
                "groq", "groq-model",
                "gemini", "gemini-model",
                "system prompt", "user message"))
                .assertNext(response -> assertThat(response).isEqualTo("Primary response"))
                .verifyComplete();

        verify(fallbackClient, never()).generate(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should fallback to secondary when primary fails")
    void callWithFallback_primaryFails_fallbackSucceeds() {
        when(primaryClient.generate(anyString(), anyString(), eq("groq-model")))
                .thenReturn(Mono.error(new RuntimeException("Primary timeout")));
        when(fallbackClient.generate(anyString(), anyString(), eq("gemini-model")))
                .thenReturn(Mono.just("Fallback response"));

        StepVerifier.create(aiFallbackService.callWithFallback(
                "groq", "groq-model",
                "gemini", "gemini-model",
                "system prompt", "user message"))
                .assertNext(response -> assertThat(response).isEqualTo("Fallback response"))
                .verifyComplete();

        verify(primaryClient).generate(anyString(), anyString(), eq("groq-model"));
        verify(fallbackClient).generate(anyString(), anyString(), eq("gemini-model"));
    }

    @Test
    @DisplayName("should propagate error when both primary and fallback fail")
    void callWithFallback_bothFail() {
        when(primaryClient.generate(anyString(), anyString(), eq("groq-model")))
                .thenReturn(Mono.error(new RuntimeException("Primary down")));
        when(fallbackClient.generate(anyString(), anyString(), eq("gemini-model")))
                .thenReturn(Mono.error(new RuntimeException("Fallback down")));

        StepVerifier.create(aiFallbackService.callWithFallback(
                "groq", "groq-model",
                "gemini", "gemini-model",
                "system prompt", "user message"))
                .expectErrorMatches(t -> t.getMessage().contains("Fallback down"))
                .verify();
    }

    @Test
    @DisplayName("should throw when provider is unsupported")
    void callWithFallback_unsupportedProvider() {
        StepVerifier.create(aiFallbackService.callWithFallback(
                "unknown-provider", "model",
                "gemini", "gemini-model",
                "system prompt", "user message"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
