package io.github.faizul;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import io.github.faizul.ai.service.AiService;

@SpringBootTest(properties = {
    "spring.ai.openai.api-key=dummy-openrouter-key",
    "spring.ai.google.genai.api-key=dummy-gemini-key"
})
class VpsPersonalBackendApplicationTests {

    @MockitoBean
    private AiService aiService;

    @Test
    void contextLoads() {
    }

}
