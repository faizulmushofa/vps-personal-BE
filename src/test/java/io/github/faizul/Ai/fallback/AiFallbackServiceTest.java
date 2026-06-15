package io.github.faizul.Ai.fallback;

import io.github.faizul.Ai.client.AiClient;
import io.github.faizul.Ai.client.AiGenerationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiFallbackServiceTest {

    private AiClient primaryClient;
    private AiClient fallback1Client;
    private AiClient fallback2Client;
    private AiFallbackService aiFallbackService;

    @BeforeEach
    void setUp() {
        primaryClient = mock(AiClient.class);
        fallback1Client = mock(AiClient.class);
        fallback2Client = mock(AiClient.class);

        lenient().when(primaryClient.supports("groq")).thenReturn(true);
        lenient().when(fallback1Client.supports("gemini")).thenReturn(true);
        lenient().when(fallback2Client.supports("groq")).thenReturn(true);

        aiFallbackService = new AiFallbackService(List.of(primaryClient, fallback1Client, fallback2Client));
    }

    @Test
    @DisplayName("should use primary provider when it succeeds")
    void callWithFallback_primarySuccess() {
        when(primaryClient.generate(anyString(), anyString(), eq("groq-model-primary")))
                .thenReturn(Mono.just(new AiGenerationResult("Primary response", 10, 10)));

        StepVerifier.withVirtualTime(() -> aiFallbackService.callWithFallback(
                "groq", "groq-model-primary",
                "gemini", "gemini-model-fb1",
                "groq", "groq-model-fb2",
                "system prompt", "user message"))
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(response -> assertThat(response.content()).isEqualTo("Primary response"))
                .verifyComplete();

        verify(fallback1Client, never()).generate(anyString(), anyString(), anyString());
        verify(fallback2Client, never()).generate(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should fallback to secondary when primary fails")
    void callWithFallback_primaryFails_fallback1Succeeds() {
        when(primaryClient.generate(anyString(), anyString(), eq("groq-model-primary")))
                .thenReturn(Mono.error(new RuntimeException("Primary timeout")));
        when(fallback1Client.generate(anyString(), anyString(), eq("gemini-model-fb1")))
                .thenReturn(Mono.just(new AiGenerationResult("Fallback 1 response", 10, 10)));

        StepVerifier.withVirtualTime(() -> aiFallbackService.callWithFallback(
                "groq", "groq-model-primary",
                "gemini", "gemini-model-fb1",
                "groq", "groq-model-fb2",
                "system prompt", "user message"))
                .thenAwait(Duration.ofSeconds(2))
                .assertNext(response -> assertThat(response.content()).isEqualTo("Fallback 1 response"))
                .verifyComplete();

        verify(primaryClient).generate(anyString(), anyString(), eq("groq-model-primary"));
        verify(fallback1Client).generate(anyString(), anyString(), eq("gemini-model-fb1"));
        verify(fallback2Client, never()).generate(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should fallback to fallback 2 when primary and fallback 1 fail")
    void callWithFallback_primaryAndFallback1Fail_fallback2Succeeds() {
        when(primaryClient.generate(anyString(), anyString(), eq("groq-model-primary")))
                .thenReturn(Mono.error(new RuntimeException("Primary timeout")));
        when(fallback1Client.generate(anyString(), anyString(), eq("gemini-model-fb1")))
                .thenReturn(Mono.error(new RuntimeException("Fallback 1 timeout")));
        when(fallback2Client.generate(anyString(), anyString(), eq("groq-model-fb2")))
                .thenReturn(Mono.just(new AiGenerationResult("Fallback 2 response", 10, 10)));

        StepVerifier.withVirtualTime(() -> aiFallbackService.callWithFallback(
                "groq", "groq-model-primary",
                "gemini", "gemini-model-fb1",
                "groq", "groq-model-fb2",
                "system prompt", "user message"))
                .thenAwait(Duration.ofSeconds(3))
                .assertNext(response -> assertThat(response.content()).isEqualTo("Fallback 2 response"))
                .verifyComplete();

        verify(primaryClient).generate(anyString(), anyString(), eq("groq-model-primary"));
        verify(fallback1Client).generate(anyString(), anyString(), eq("gemini-model-fb1"));
        verify(fallback2Client).generate(anyString(), anyString(), eq("groq-model-fb2"));
    }

    @Test
    @DisplayName("should propagate error when all models fail")
    void callWithFallback_allFail() {
        when(primaryClient.generate(anyString(), anyString(), eq("groq-model-primary")))
                .thenReturn(Mono.error(new RuntimeException("Primary down")));
        when(fallback1Client.generate(anyString(), anyString(), eq("gemini-model-fb1")))
                .thenReturn(Mono.error(new RuntimeException("Fallback 1 down")));
        when(fallback2Client.generate(anyString(), anyString(), eq("groq-model-fb2")))
                .thenReturn(Mono.error(new RuntimeException("Fallback 2 down")));

        StepVerifier.withVirtualTime(() -> aiFallbackService.callWithFallback(
                "groq", "groq-model-primary",
                "gemini", "gemini-model-fb1",
                "groq", "groq-model-fb2",
                "system prompt", "user message"))
                .thenAwait(Duration.ofSeconds(3))
                .expectErrorMatches(t -> t.getMessage().contains("Fallback 2 down"))
                .verify();
    }

    @Test
    @DisplayName("should throw when provider is unsupported")
    void callWithFallback_unsupportedProvider() {
        StepVerifier.create(aiFallbackService.callWithFallback(
                "unknown-provider", "model",
                "gemini", "gemini-model-fb1",
                "groq", "groq-model-fb2",
                "system prompt", "user message"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
