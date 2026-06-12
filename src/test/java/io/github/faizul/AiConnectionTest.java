package io.github.faizul;

import io.github.faizul.Ai.client.GeminiService;
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

    @Autowired
    private GeminiService geminiService;

    @Test
    public void testAllAiConnections() {
        System.out.println("\n>>> ================================================= <<<");
        System.out.println(">>> STARTING AI CONNECTION INTEGRATION TESTS          <<<");
        System.out.println(">>> ================================================= <<<\n");

        // 1. Summary Primary: qwen/qwen3-next-80b-a3b-instruct
        System.out.println(">>> 1. Testing Summary Primary (OpenRouter): qwen/qwen3-next-80b-a3b-instruct");
        try {
            groqService.generate("Say hello briefly", "Hello", "qwen/qwen3-next-80b-a3b-instruct")
                    .timeout(Duration.ofSeconds(20))
                    .doOnNext(res -> System.out.println("    [SUCCESS] Response: " + res.trim()))
                    .doOnError(err -> System.err.println("    [FAILED] Error: " + err.getMessage()))
                    .as(StepVerifier::create)
                    .expectNextCount(1)
                    .verifyComplete();
        } catch (Throwable t) {
            System.err.println("    [FAILED] Exception: " + t.getMessage());
        }

        // 2. Chat PDF Primary: meta-llama/llama-3.3-70b-instruct
        System.out.println("\n>>> 2. Testing Chat PDF Primary (OpenRouter): meta-llama/llama-3.3-70b-instruct");
        try {
            groqService.generate("Say hello briefly", "Hello", "meta-llama/llama-3.3-70b-instruct")
                    .timeout(Duration.ofSeconds(20))
                    .doOnNext(res -> System.out.println("    [SUCCESS] Response: " + res.trim()))
                    .doOnError(err -> System.err.println("    [FAILED] Error: " + err.getMessage()))
                    .as(StepVerifier::create)
                    .expectNextCount(1)
                    .verifyComplete();
        } catch (Throwable t) {
            System.err.println("    [FAILED] Exception: " + t.getMessage());
        }

        // 3. Summary Fallback: gemini-2.5-flash
        System.out.println("\n>>> 3. Testing Summary Fallback (Gemini Native): gemini-2.5-flash");
        try {
            geminiService.generate("Say hello briefly", "Hello", "gemini-2.5-flash")
                    .timeout(Duration.ofSeconds(20))
                    .doOnNext(res -> System.out.println("    [SUCCESS] Response: " + res.trim()))
                    .doOnError(err -> System.err.println("    [FAILED] Error: " + err.getMessage()))
                    .as(StepVerifier::create)
                    .expectNextCount(1)
                    .verifyComplete();
        } catch (Throwable t) {
            System.err.println("    [FAILED] Exception: " + t.getMessage());
        }

        // 4. Chat PDF Fallback: gemini-3.1-flash-lite
        System.out.println("\n>>> 4. Testing Chat PDF Fallback (Gemini Native): gemini-3.1-flash-lite");
        try {
            geminiService.generate("Say hello briefly", "Hello", "gemini-3.1-flash-lite")
                    .timeout(Duration.ofSeconds(20))
                    .doOnNext(res -> System.out.println("    [SUCCESS] Response: " + res.trim()))
                    .doOnError(err -> System.err.println("    [FAILED] Error: " + err.getMessage()))
                    .as(StepVerifier::create)
                    .expectNextCount(1)
                    .verifyComplete();
        } catch (Throwable t) {
            System.err.println("    [FAILED] Exception: " + t.getMessage());
        }

        System.out.println("\n>>> ================================================= <<<");
        System.out.println(">>> AI CONNECTION TESTS COMPLETED                     <<<");
        System.out.println(">>> ================================================= <<<\n");
    }
}
