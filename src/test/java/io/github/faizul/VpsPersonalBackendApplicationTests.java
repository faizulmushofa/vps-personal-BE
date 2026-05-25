package io.github.faizul;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.google.genai.Client;

@SpringBootTest
class VpsPersonalBackendApplicationTests {

    @MockitoBean
    private Client geminiClient;

    @Test
    void contextLoads() {
    }

}
