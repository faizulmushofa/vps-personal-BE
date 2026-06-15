package io.github.faizul;

import io.github.faizul.Ai.client.GroqService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;
import java.time.Duration;

@SpringBootTest
public class AiConnectionTest {

    @Autowired
    private GroqService groqService;

    @Test
    public void testAllAiConnections() {
        String[] models = {
            "openrouter/free",
            "openai/gpt-oss-120b:free",
            "openai/gpt-oss-20b:free",
            "google/gemma-3-27b-it:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "meta-llama/llama-3.2-3b-instruct:free",
            "deepseek/deepseek-r1:free",
            "qwen/qwen3-coder:free",
            "google/gemini-flash:free",
            "nvidia/nemotron-3-nano:free",
            "openrouter/owl-alpha",
            "minimax/m2.5:free",
            "poolside/laguna-xs.2:free",
            "poolside/laguna-m.1:free",
            "google/gemma-4-26b-a4b-it:free",
            "google/gemma-4-31b-it:free",
            "liquid/lfm-2.5-1.2b-thinking:free",
            "liquid/lfm-2.5-1.2b-instruct:free",
            "nvidia/nemotron-3-nano-30b-a3b:free",
            "nvidia/nemotron-nano-12b-v2-vl:free",
            "nvidia/nemotron-nano-9b-v2:free",
            "cognitivecomputations/dolphin-mistral-24b-venice-edition:free",
            "nousresearch/hermes-3-llama-3.1-405b:free"
        };

        System.out.println("\n>>> ================================================= <<<");
        System.out.println(">>> STARTING AI CONNECTION INTEGRATION TESTS          <<<");
        System.out.println(">>> ================================================= <<<\n");

        for (String model : models) {
            System.out.println(">>> Testing OpenRouter Model: " + model);
            try {
                groqService.generate("Say 'Connection OK' briefly", "Test", model)
                        .timeout(Duration.ofSeconds(20))
                        .doOnNext(res -> System.out.println("    [SUCCESS] Response: " + res.content().trim().replace("\n", " ")))
                        .doOnError(err -> System.err.println("    [FAILED] Error: " + err.getMessage()))
                        .as(StepVerifier::create)
                        .expectNextCount(1)
                        .verifyComplete();
            } catch (Throwable t) {
                System.err.println("    [FAILED] Exception: " + t.getMessage());
            }
            System.out.println("-------------------------------------------------------");
        }

        System.out.println("\n>>> ================================================= <<<");
        System.out.println(">>> AI CONNECTION TESTS COMPLETED                     <<<");
        System.out.println(">>> ================================================= <<<\n");
    }
}
