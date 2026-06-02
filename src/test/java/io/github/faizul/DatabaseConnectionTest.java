package io.github.faizul;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.google.genai.Client;
import reactor.test.StepVerifier;

@SpringBootTest
public class DatabaseConnectionTest {

    @MockitoBean
    private Client geminiClient;

    @Autowired
    private DatabaseClient databaseClient;

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
