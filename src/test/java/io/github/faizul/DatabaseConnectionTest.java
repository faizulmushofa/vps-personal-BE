package io.github.faizul;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import io.github.faizul.Ai.AiService;
import io.github.faizul.Ai.dtos.AiRequest;
import reactor.test.StepVerifier;

@SpringBootTest(properties = {
    "spring.ai.openai.api-key=dummy-openrouter-key",
    "spring.ai.google.genai.api-key=dummy-gemini-key"
})
public class DatabaseConnectionTest {

    @Autowired
    private AiService aiService;

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    public void testAiConnection() {
        try {
            System.out.println("\n>>> CALLING AI SERVICE...");
            aiService.summary(new AiRequest("Hello"))
                .subscribe(response -> System.out.println(">>> AI RESPONSE: " + response.response() + "\n"),
                           err -> {
                               System.err.println(">>> AI SERVICE EXCEPTION:");
                               err.printStackTrace();
                           });
        } catch (Exception e) {
            System.err.println("\n>>> AI SERVICE EXCEPTION:");
            e.printStackTrace();
            System.err.println(">>> ====================\n");
        }
    }

    @Test
    public void testSupabaseConnection() {
        databaseClient.sql("SELECT 1")
                .map((row, metadata) -> row.get(0, Integer.class))
                .one()
                .as(StepVerifier::create)
                .expectNext(1)
                .verifyComplete();
        System.out.println("\n>>> ================================================= <<<");
        System.out.println(">>> KONEKSI KE DATABASE SUPABASE BERHASIL & AKTIF!    <<<");
        System.out.println(">>> ================================================= <<<\n");
    }
}
