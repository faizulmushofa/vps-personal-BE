package io.github.faizul.security.jwt;

import io.github.faizul.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import io.github.faizul.security.jwt.model.RefreshToken;
import io.github.faizul.security.jwt.repository.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;

    private JwtService jwtService;
    private User testUser;

    // Must be at least 32 bytes for HMAC-SHA256
    private static final String TEST_SECRET = "ThisIsATestSecretKeyForJwtTesting12345678";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(refreshTokenRepository);
        ReflectionTestUtils.setField(jwtService, "SECRET", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiryHours", 1L);

        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .username("testuser")
                .password("hashedPassword")
                .build();
    }

    @Nested
    @DisplayName("generateAccessToken")
    class GenerateAccessTokenTests {

        @Test
        @DisplayName("should generate a valid JWT access token")
        void generateAccessToken_success() {
            String token = jwtService.generateAccessToken(testUser);

            assertThat(token).isNotNull().isNotBlank();
            // The token should have 3 parts separated by dots
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("should include email as subject in token")
        void generateAccessToken_containsEmail() {
            String token = jwtService.generateAccessToken(testUser);
            String email = jwtService.extractEmail(token);

            assertThat(email).isEqualTo("test@example.com");
        }
    }

    @Nested
    @DisplayName("extractEmail")
    class ExtractEmailTests {

        @Test
        @DisplayName("should extract email from valid token")
        void extractEmail_success() {
            String token = jwtService.generateAccessToken(testUser);
            String email = jwtService.extractEmail(token);

            assertThat(email).isEqualTo("test@example.com");
        }
    }

    @Nested
    @DisplayName("isValid")
    class IsValidTests {

        @Test
        @DisplayName("should return true for valid non-expired token")
        void isValid_validToken() {
            String token = jwtService.generateAccessToken(testUser);
            boolean valid = jwtService.isValid(token);

            assertThat(valid).isTrue();
        }

        @Test
        @DisplayName("should return false for malformed token")
        void isValid_malformedToken() {
            boolean valid = jwtService.isValid("not.a.valid.token");

            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("should return false for empty token")
        void isValid_emptyToken() {
            boolean valid = jwtService.isValid("");

            assertThat(valid).isFalse();
        }
    }

    @Nested
    @DisplayName("generateRefreshToken")
    class GenerateRefreshTokenTests {

        @Test
        @DisplayName("should generate and save refresh token")
        void generateRefreshToken_success() {
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(jwtService.generateRefreshToken(testUser))
                    .assertNext(token -> assertThat(token).isNotNull().isNotBlank())
                    .verifyComplete();

            verify(refreshTokenRepository).save(argThat(rt ->
                    rt.getUserId().equals(1L) &&
                    !rt.getRevoked() &&
                    rt.getToken() != null));
        }
    }

    @Nested
    @DisplayName("refresh")
    class RefreshTests {

        @Test
        @DisplayName("should revoke old token and create new one")
        void refresh_success() {
            RefreshToken existing = RefreshToken.builder()
                    .id(1L)
                    .userId(1L)
                    .token("old-token")
                    .revoked(false)
                    .createdAt(LocalDateTime.now())
                    .expiredAt(LocalDateTime.now().plusDays(3))
                    .build();

            when(refreshTokenRepository.findByToken("old-token")).thenReturn(Mono.just(existing));
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(jwtService.refresh("old-token"))
                    .assertNext(newToken -> {
                        assertThat(newToken.getUserId()).isEqualTo(1L);
                        assertThat(newToken.getRevoked()).isFalse();
                        assertThat(newToken.getToken()).isNotEqualTo("old-token");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should throw error when token not found")
        void refresh_tokenNotFound() {
            when(refreshTokenRepository.findByToken("invalid-token")).thenReturn(Mono.empty());

            StepVerifier.create(jwtService.refresh("invalid-token"))
                    .expectError(BadCredentialsException.class)
                    .verify();
        }

        @Test
        @DisplayName("should throw error when token is already revoked")
        void refresh_revokedToken() {
            RefreshToken revokedToken = RefreshToken.builder()
                    .id(1L).userId(1L).token("revoked-token")
                    .revoked(true)
                    .createdAt(LocalDateTime.now())
                    .expiredAt(LocalDateTime.now().plusDays(3))
                    .build();

            when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Mono.just(revokedToken));

            StepVerifier.create(jwtService.refresh("revoked-token"))
                    .expectError(BadCredentialsException.class)
                    .verify();
        }
    }
}
