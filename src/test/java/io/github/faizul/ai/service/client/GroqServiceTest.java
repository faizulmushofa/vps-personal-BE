package io.github.faizul.ai.service.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GroqServiceTest {

    @Mock private ChatModel chatModel;

    @Test
    @DisplayName("should support 'groq' and 'openrouter' providers")
    void supports_groq() {
        GroqService service = new GroqService(chatModel);
        assertThat(service.supports("groq")).isTrue();
        assertThat(service.supports("GROQ")).isTrue();
        assertThat(service.supports("openrouter")).isTrue();
        assertThat(service.supports("OPENROUTER")).isTrue();
    }

    @Test
    @DisplayName("should not support other providers")
    void supports_otherProviders() {
        GroqService service = new GroqService(chatModel);
        assertThat(service.supports("gemini")).isFalse();
        assertThat(service.supports("openai")).isFalse();
    }
}
