package io.github.faizul.security.auth;

import io.github.faizul.User.core.User;
import io.github.faizul.User.core.UserRepository;
import io.github.faizul.User.core.UserService;
import io.github.faizul.User.dtos.UserDto;
import io.github.faizul.notification.NotificationService;
import io.github.faizul.security.auth.dtos.*;
import io.github.faizul.security.auth.otp.OtpVerification;
import io.github.faizul.security.auth.otp.OtpVerificationRepository;
import io.github.faizul.security.jwt.JwtService;
import io.github.faizul.security.jwt.RefreshToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private UserService userService;
    @Mock private OtpVerificationRepository otpVerificationRepository;
    @Mock private NotificationService notificationService;
    @Mock private io.github.faizul.activity.UserActivityService userActivityService;

    @InjectMocks
    private AuthService authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("hashedPassword")
                .fullName("Test User")
                .isActive(true)
                .build();

        lenient().when(userActivityService.log(any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("should register user and send OTP email")
        void register_success() {
            RegisterRequest request = new RegisterRequest(
                    "newuser", "new@example.com", "password123", "New User", "081234567890");

            UserDto createdDto = new UserDto(1L, "newuser", "new@example.com",
                    "New User", null, "081234567890", null, false, List.of("USER"),
                    LocalDateTime.now(), null, null, "FREEMIUM", null);

            when(userRepository.findByEmail(anyString())).thenReturn(Mono.empty());
            when(userService.createUser(any(User.class))).thenReturn(Mono.just(createdDto));
            when(otpVerificationRepository.save(any(OtpVerification.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(notificationService.sendNotification(anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());

            StepVerifier.create(authService.register(request, null))
                    .assertNext(response -> assertThat(response.response()).contains("Register Successfully"))
                    .verifyComplete();

            verify(otpVerificationRepository).save(any(OtpVerification.class));
            verify(notificationService).sendNotification(eq("new@example.com"), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("verifyRegistration")
    class VerifyRegistrationTests {

        @Test
        @DisplayName("should activate user on valid OTP")
        void verifyRegistration_success() {
            VerifyOtpRequest request = new VerifyOtpRequest("test@example.com", "123456");

            OtpVerification otp = OtpVerification.builder()
                    .id(1L)
                    .email("test@example.com")
                    .otpCode("123456")
                    .type("REGISTRATION")
                    .expiryTime(LocalDateTime.now().plusMinutes(5))
                    .verified(false)
                    .build();

            User inactiveUser = User.builder()
                    .id(1L).email("test@example.com").isActive(false).build();

            when(otpVerificationRepository.findLatestUnverified("test@example.com", "REGISTRATION"))
                    .thenReturn(Mono.just(otp));
            when(userRepository.findByEmail("test@example.com")).thenReturn(Mono.just(inactiveUser));
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(inactiveUser));
            when(otpVerificationRepository.save(any(OtpVerification.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(authService.verifyRegistration(request, null))
                    .assertNext(response -> assertThat(response.response()).contains("Verifikasi email berhasil"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should throw error when OTP is expired")
        void verifyRegistration_expiredOtp() {
            VerifyOtpRequest request = new VerifyOtpRequest("test@example.com", "123456");

            OtpVerification expiredOtp = OtpVerification.builder()
                    .email("test@example.com")
                    .otpCode("123456")
                    .type("REGISTRATION")
                    .expiryTime(LocalDateTime.now().minusMinutes(1))
                    .verified(false)
                    .build();

            when(otpVerificationRepository.findLatestUnverified("test@example.com", "REGISTRATION"))
                    .thenReturn(Mono.just(expiredOtp));

            StepVerifier.create(authService.verifyRegistration(request, null))
                    .expectErrorMatches(t -> t instanceof IllegalArgumentException &&
                            t.getMessage().contains("kadaluarsa"))
                    .verify();
        }

        @Test
        @DisplayName("should throw error when OTP not found")
        void verifyRegistration_otpNotFound() {
            VerifyOtpRequest request = new VerifyOtpRequest("test@example.com", "000000");

            when(otpVerificationRepository.findLatestUnverified("test@example.com", "REGISTRATION"))
                    .thenReturn(Mono.empty());

            StepVerifier.create(authService.verifyRegistration(request, null))
                    .expectErrorMatches(t -> t instanceof IllegalArgumentException &&
                            t.getMessage().contains("OTP tidak valid"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("login")
    class LoginTests {

        @Test
        @DisplayName("should return tokens on valid credentials")
        void login_success() {
            LoginRequest request = new LoginRequest("test@example.com", "correctPassword");

            when(userRepository.findByEmail("test@example.com")).thenReturn(Mono.just(activeUser));
            when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);
            when(jwtService.generateAccessToken(activeUser)).thenReturn("access-token-abc");
            when(jwtService.revokeAllUserTokens(activeUser.getId())).thenReturn(Mono.empty());
            when(jwtService.generateRefreshToken(activeUser)).thenReturn(Mono.just("refresh-token-xyz"));

            StepVerifier.create(authService.login(request, null))
                    .assertNext(response -> {
                        assertThat(response.accessToken()).isEqualTo("access-token-abc");
                        assertThat(response.refreshToken()).isEqualTo("refresh-token-xyz");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should throw error on wrong password")
        void login_wrongPassword() {
            LoginRequest request = new LoginRequest("test@example.com", "wrongPassword");

            when(userRepository.findByEmail("test@example.com")).thenReturn(Mono.just(activeUser));
            when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

            StepVerifier.create(authService.login(request, null))
                    .expectError(UsernameNotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("should throw error when user is not active")
        void login_inactiveUser() {
            LoginRequest request = new LoginRequest("test@example.com", "correctPassword");
            activeUser.setIsActive(false);

            when(userRepository.findByEmail("test@example.com")).thenReturn(Mono.just(activeUser));
            when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);

            StepVerifier.create(authService.login(request, null))
                    .expectErrorMatches(t -> t instanceof IllegalArgumentException &&
                            t.getMessage().contains("belum aktif"))
                    .verify();
        }

        @Test
        @DisplayName("should throw error when email not found")
        void login_emailNotFound() {
            LoginRequest request = new LoginRequest("unknown@example.com", "password");

            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Mono.empty());

            StepVerifier.create(authService.login(request, null))
                    .expectError(UsernameNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("requestForgotPassword")
    class ForgotPasswordTests {

        @Test
        @DisplayName("should send OTP for forgot password")
        void requestForgotPassword_success() {
            ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com");

            when(userRepository.findByEmail("test@example.com")).thenReturn(Mono.just(activeUser));
            when(otpVerificationRepository.save(any(OtpVerification.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(notificationService.sendNotification(anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());

            StepVerifier.create(authService.requestForgotPassword(request, null))
                    .assertNext(response -> assertThat(response.response()).contains("OTP pemulihan"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should throw error when email not found")
        void requestForgotPassword_emailNotFound() {
            ForgotPasswordRequest request = new ForgotPasswordRequest("unknown@example.com");

            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Mono.empty());

            StepVerifier.create(authService.requestForgotPassword(request, null))
                    .expectError(UsernameNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("resetPassword")
    class ResetPasswordTests {

        @Test
        @DisplayName("should reset password on valid OTP")
        void resetPassword_success() {
            ResetPasswordRequest request = new ResetPasswordRequest(
                    "test@example.com", "123456", "newPassword123");

            OtpVerification otp = OtpVerification.builder()
                    .email("test@example.com")
                    .otpCode("123456")
                    .type("FORGOT_PASSWORD")
                    .expiryTime(LocalDateTime.now().plusMinutes(5))
                    .verified(false)
                    .build();

            when(otpVerificationRepository.findLatestUnverified("test@example.com", "FORGOT_PASSWORD"))
                    .thenReturn(Mono.just(otp));
            when(userRepository.findByEmail("test@example.com")).thenReturn(Mono.just(activeUser));
            when(passwordEncoder.encode("newPassword123")).thenReturn("newHashedPassword");
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(activeUser));
            when(otpVerificationRepository.save(any(OtpVerification.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(authService.resetPassword(request, null))
                    .assertNext(response -> assertThat(response.response()).contains("berhasil diperbarui"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should throw error when reset OTP is expired")
        void resetPassword_expiredOtp() {
            ResetPasswordRequest request = new ResetPasswordRequest(
                    "test@example.com", "123456", "newPassword123");

            OtpVerification expiredOtp = OtpVerification.builder()
                    .email("test@example.com")
                    .expiryTime(LocalDateTime.now().minusMinutes(1))
                    .verified(false)
                    .build();

            when(otpVerificationRepository.findLatestUnverified("test@example.com", "FORGOT_PASSWORD"))
                    .thenReturn(Mono.just(expiredOtp));

            StepVerifier.create(authService.resetPassword(request, null))
                    .expectErrorMatches(t -> t instanceof IllegalArgumentException &&
                            t.getMessage().contains("kadaluarsa"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("refresh")
    class RefreshTests {

        @Test
        @DisplayName("should return new tokens on valid refresh token")
        void refresh_success() {
            RefreshToken newRefreshToken = RefreshToken.builder()
                    .id(2L).userId(1L).token("new-refresh-token")
                    .revoked(false).createdAt(LocalDateTime.now())
                    .expiredAt(LocalDateTime.now().plusDays(3))
                    .build();

            when(jwtService.refresh("old-refresh-token")).thenReturn(Mono.just(newRefreshToken));
            when(userRepository.findById(1L)).thenReturn(Mono.just(activeUser));
            when(jwtService.generateAccessToken(activeUser)).thenReturn("new-access-token");

            StepVerifier.create(authService.refresh("old-refresh-token"))
                    .assertNext(response -> {
                        assertThat(response.accessToken()).isEqualTo("new-access-token");
                        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
                        assertThat(response.message()).isEqualTo("token refreshed");
                    })
                    .verifyComplete();
        }
    }
}
