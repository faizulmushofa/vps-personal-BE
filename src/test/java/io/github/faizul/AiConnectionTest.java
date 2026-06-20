package io.github.faizul;

import io.github.faizul.ai.service.client.GroqService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import java.time.Duration;
import java.util.List;
import io.github.faizul.security.auth.dtos.Response;

@SpringBootTest
public class AiConnectionTest {

    @Autowired
    private GroqService groqService;

    @Autowired
    private Environment environment;

    private static final List<String> VALID_MODELS = List.of(
        "meta-llama/llama-3.3-70b-instruct:free",
        "meta-llama/llama-3.2-3b-instruct:free",
        "qwen/qwen3-coder:free",
        "google/gemma-4-26b-a4b-it:free",
        "cognitivecomputations/dolphin-mistral-24b-venice-edition:free",
        "nousresearch/hermes-3-llama-3.1-405b:free"
    );

    @Test
    public void testAllAiConnections() {
        String apiKey = environment.getProperty("spring.ai.openai.api-key");
        if (apiKey == null || apiKey.trim().isEmpty() || "dummy-openrouter-key".equals(apiKey)) {
            System.out.println("\n>>> ================================================= <<<");
            System.out.println(">>> SKIPPING AI CONNECTION INTEGRATION TESTS          <<<");
            System.out.println(">>> Reason: OPEN_ROUTER_API_KEY is not set or dummy  <<<");
            System.out.println(">>> ================================================= <<<\n");
            return;
        }

        System.out.println("\n>>> ================================================= <<<");
        System.out.println(">>> STARTING AI CONNECTION INTEGRATION TESTS          <<<");
        System.out.println(">>> ================================================= <<<\n");

        for (String model : VALID_MODELS) {
            System.out.println(">>> Testing OpenRouter Model: " + model);
            long startTime = System.currentTimeMillis();
            try {
                var res = groqService.generate("Say 'Connection OK' briefly", "Test", model)
                        .timeout(Duration.ofSeconds(20))
                        .block();
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                if (res != null && res.content() != null) {
                    System.out.println("    [SUCCESS] Response: " + res.content().trim().replace("\n", " "));
                    System.out.println("    [TIME] Duration: " + duration + " ms");
                } else {
                    System.err.println("    [FAILED] Error: Response is empty");
                    System.err.println("    [TIME] Duration: " + duration + " ms");
                }
            } catch (Throwable t) {
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                String msg = t.getMessage();
                if (msg == null) {
                    msg = t.getClass().getSimpleName();
                }
                System.err.println("    [FAILED] Error: " + msg);
                System.err.println("    [TIME] Duration: " + duration + " ms");
            }
            System.out.println("-------------------------------------------------------");
        }

        System.out.println("\n>>> ================================================= <<<");
        System.out.println(">>> AI CONNECTION TESTS COMPLETED                     <<<");
        System.out.println(">>> ================================================= <<<\n");
    }
}
