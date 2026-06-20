package io.github.faizul.ai.service.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {

    @Mock private ChatModel chatModel;

    @Test
    @DisplayName("should support 'gemini' provider")
    void supports_gemini() {
        GeminiService service = new GeminiService(chatModel);
        assertThat(service.supports("gemini")).isTrue();
        assertThat(service.supports("GEMINI")).isTrue();
    }

    @Test
    @DisplayName("should not support other providers")
    void supports_otherProviders() {
        GeminiService service = new GeminiService(chatModel);
        assertThat(service.supports("groq")).isFalse();
        assertThat(service.supports("openai")).isFalse();
    }
}
