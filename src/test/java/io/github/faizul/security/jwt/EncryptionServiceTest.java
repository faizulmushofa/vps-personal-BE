package io.github.faizul.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptionServiceTest {

    private EncryptionService encryptionService;
    private static final String TEST_SECRET = "bZR8PUQpkBVnGietYoSEMj5nyJQvDn3R3KfRmmJ/VjocR/mciQK97MDvfAlHVOB2";

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService(TEST_SECRET);
    }

    @Test
    @DisplayName("should encrypt and decrypt string successfully")
    void testEncryptDecryptSuccess() {
        String originalText = "my-secret-oauth-token-123456";
        String encrypted = encryptionService.encrypt(originalText);

        assertThat(encrypted).isNotNull().isNotBlank();
        assertThat(encrypted).isNotEqualTo(originalText);

        String decrypted = encryptionService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(originalText);
    }

    @Test
    @DisplayName("should produce different ciphertexts for the same plaintext due to random IV")
    void testEncryptProducesDifferentCiphertexts() {
        String originalText = "same-secret-token";
        String encrypted1 = encryptionService.encrypt(originalText);
        String encrypted2 = encryptionService.encrypt(originalText);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
        assertThat(encryptionService.decrypt(encrypted1)).isEqualTo(originalText);
        assertThat(encryptionService.decrypt(encrypted2)).isEqualTo(originalText);
    }

    @Test
    @DisplayName("should handle null or empty values gracefully")
    void testHandleNullAndEmpty() {
        assertThat(encryptionService.encrypt(null)).isNull();
        assertThat(encryptionService.encrypt("")).isEmpty();
        assertThat(encryptionService.decrypt(null)).isNull();
        assertThat(encryptionService.decrypt("")).isEmpty();
    }

    @Test
    @DisplayName("should fallback to plaintext when decryption fails (backward compatibility)")
    void testDecryptFallbackToPlaintext() {
        String plainTextToken = "ya29.a0ARWdsHjL...";
        String decrypted = encryptionService.decrypt(plainTextToken);
        assertThat(decrypted).isEqualTo(plainTextToken);
    }
}
