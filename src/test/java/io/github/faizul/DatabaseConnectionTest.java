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
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
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

    @Test
    public void testUsersTable() {
        System.out.println("\n>>> ================================================= <<<");
        System.out.println(">>> MEMBACA ISI TABEL USERS...                         <<<");
        databaseClient.sql("SELECT id, username, email, password, is_active FROM users")
                .map((row, metadata) -> {
                    System.out.println("USER ROW -> id: " + row.get("id") + 
                                       ", username: " + row.get("username") + 
                                       ", email: " + row.get("email") + 
                                       ", password_hash: " + row.get("password") + 
                                       ", is_active: " + row.get("is_active"));
                    return row.get("email", String.class);
                })
                .all()
                .collectList()
                .doOnError(err -> System.err.println(">>> ERROR MEMBACA TABEL USERS: " + err.getMessage()))
                .block();
        System.out.println(">>> ================================================= <<<\n");
    }

    @Test
    public void testFilesAndSharesTable() {
        System.out.println("\n>>> ================================================= <<<");
        System.out.println(">>> MEMBACA ISI TABEL FILES...                         <<<");
        databaseClient.sql("SELECT id, original_file_name, size, provider, user_id FROM files")
                .map((row, metadata) -> {
                    System.out.println("FILE ROW -> id: " + row.get("id") + 
                                       ", name: " + row.get("original_file_name") + 
                                       ", size: " + row.get("size") + 
                                       ", provider: " + row.get("provider") +
                                       ", user_id: " + row.get("user_id"));
                    return row.get("id", String.class);
                })
                .all()
                .collectList()
                .doOnError(err -> System.err.println(">>> ERROR MEMBACA TABEL FILES: " + err.getMessage()))
                .block();

        System.out.println(">>> MEMBACA ISI TABEL FILE_SHARED...                  <<<");
        databaseClient.sql("SELECT id, file_id, shared_with_email, is_public, share_link FROM file_shared")
                .map((row, metadata) -> {
                    System.out.println("SHARE ROW -> id: " + row.get("id") + 
                                       ", file_id: " + row.get("file_id") + 
                                       ", email: " + row.get("shared_with_email") + 
                                       ", is_public: " + row.get("is_public") +
                                       ", link: " + row.get("share_link"));
                    return row.get("id", Integer.class);
                })
                .all()
                .collectList()
                .doOnError(err -> System.err.println(">>> ERROR MEMBACA TABEL FILE_SHARED: " + err.getMessage()))
                .block();
        System.out.println(">>> ================================================= <<<\n");
    }

    @Test
    public void testMigrationTasks() {
        System.out.println("\n>>> ================================================= <<<");
        System.out.println(">>> MEMBACA ISI TABEL MIGRATION_TASKS...               <<<");
        databaseClient.sql("SELECT id, file_id, file_name, status, error_message, source_provider, target_provider, created_at FROM migration_tasks ORDER BY created_at DESC LIMIT 10")
                .map((row, metadata) -> {
                    System.out.println("TASK ROW -> id: " + row.get("id") + 
                                       ", file_id: " + row.get("file_id") + 
                                       ", name: " + row.get("file_name") + 
                                       ", status: " + row.get("status") + 
                                       ", error: " + row.get("error_message") +
                                       ", source: " + row.get("source_provider") +
                                       ", target: " + row.get("target_provider") +
                                       ", created: " + row.get("created_at"));
                    return row.get("id", java.util.UUID.class);
                })
                .all()
                .collectList()
                .doOnError(err -> System.err.println(">>> ERROR MEMBACA TABEL MIGRATION_TASKS: " + err.getMessage()))
                .block();
        System.out.println(">>> ================================================= <<<\n");
    }
}


