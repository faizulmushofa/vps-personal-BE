package io.github.faizul.security.auth.service;

import io.github.faizul.notification.service.NotificationService;
import io.github.faizul.security.auth.otp.OtpVerification;
import io.github.faizul.security.auth.otp.OtpVerificationRepository;
import io.github.faizul.security.auth.service.impl.AcademicVerificationServiceImpl;
import io.github.faizul.user.model.AcademicDomain;
import io.github.faizul.user.model.User;
import io.github.faizul.user.repository.AcademicDomainRepository;
import io.github.faizul.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcademicVerificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AcademicDomainRepository academicDomainRepository;

    @Mock
    private OtpVerificationRepository otpVerificationRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AcademicVerificationServiceImpl academicVerificationService;

    private AcademicDomain defaultDomain1;
    private AcademicDomain defaultDomain2;

    @BeforeEach
    void setUp() {
        defaultDomain1 = AcademicDomain.builder().id(1L).domain("ac.id").build();
        defaultDomain2 = AcademicDomain.builder().id(2L).domain("edu").build();
    }

    @Test
    @DisplayName("should send OTP successfully when domain is valid and email is unique")
    void testSendOtpSuccess() {
        String email = "student@ui.ac.id";
        Long userId = 1L;

        when(userRepository.existsByAcademicEmail(email.toLowerCase())).thenReturn(Mono.just(false));
        when(academicDomainRepository.findAll()).thenReturn(Flux.just(defaultDomain1, defaultDomain2));
        when(otpVerificationRepository.invalidateAllUnverified(anyString(), anyString())).thenReturn(Mono.empty());
        when(otpVerificationRepository.save(any(OtpVerification.class))).thenAnswer(invocation -> {
            OtpVerification otp = invocation.getArgument(0);
            otp.setId(100L);
            return Mono.just(otp);
        });
        when(notificationService.sendNotification(eq(email.toLowerCase()), anyString(), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(academicVerificationService.sendOtp(email, userId))
                .verifyComplete();

        verify(otpVerificationRepository).invalidateAllUnverified(email.toLowerCase(), "ACADEMIC_VERIFICATION");
        verify(otpVerificationRepository).save(any(OtpVerification.class));
        verify(notificationService).sendNotification(eq(email.toLowerCase()), anyString(), anyString());
    }

    @Test
    @DisplayName("should throw error when academic email already in use")
    void testSendOtpAlreadyUsed() {
        String email = "student@ui.ac.id";
        Long userId = 1L;

        when(userRepository.existsByAcademicEmail(email.toLowerCase())).thenReturn(Mono.just(true));

        StepVerifier.create(academicVerificationService.sendOtp(email, userId))
                .expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException 
                        && throwable.getMessage().contains("Email akademik ini sudah digunakan"))
                .verify();

        verify(academicDomainRepository, never()).findAll();
        verify(otpVerificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw error when domain is not registered as academic domain")
    void testSendOtpInvalidDomain() {
        String email = "student@gmail.com";
        Long userId = 1L;

        when(userRepository.existsByAcademicEmail(email.toLowerCase())).thenReturn(Mono.just(false));
        when(academicDomainRepository.findAll()).thenReturn(Flux.just(defaultDomain1, defaultDomain2));

        StepVerifier.create(academicVerificationService.sendOtp(email, userId))
                .expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException 
                        && throwable.getMessage().contains("Domain email tidak terdaftar"))
                .verify();

        verify(otpVerificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("should verify OTP successfully and update user status")
    void testVerifyOtpSuccess() {
        String email = "student@ui.ac.id";
        String otpCode = "123456";
        Long userId = 1L;

        OtpVerification otp = OtpVerification.builder()
                .id(100L)
                .email(email.toLowerCase())
                .otpCode(otpCode)
                .type("ACADEMIC_VERIFICATION")
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .verified(false)
                .build();

        User user = User.builder()
                .id(userId)
                .username("student_user")
                .email("main@gmail.com")
                .studentVerified(false)
                .build();

        when(otpVerificationRepository.findLatestUnverified(email.toLowerCase(), "ACADEMIC_VERIFICATION"))
                .thenReturn(Mono.just(otp));
        when(otpVerificationRepository.invalidateAllUnverified(email.toLowerCase(), "ACADEMIC_VERIFICATION"))
                .thenReturn(Mono.empty());
        when(userRepository.findById(userId)).thenReturn(Mono.just(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(academicVerificationService.verifyOtp(email, otpCode, userId))
                .verifyComplete();

        assertThat(user.getStudentVerified()).isTrue();
        assertThat(user.getAcademicEmail()).isEqualTo(email.toLowerCase());
        verify(otpVerificationRepository).invalidateAllUnverified(email.toLowerCase(), "ACADEMIC_VERIFICATION");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("should throw error when OTP is incorrect")
    void testVerifyOtpWrongCode() {
        String email = "student@ui.ac.id";
        String otpCode = "123456";
        Long userId = 1L;

        OtpVerification otp = OtpVerification.builder()
                .id(100L)
                .email(email.toLowerCase())
                .otpCode("654321") // different code
                .type("ACADEMIC_VERIFICATION")
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .verified(false)
                .build();

        when(otpVerificationRepository.findLatestUnverified(email.toLowerCase(), "ACADEMIC_VERIFICATION"))
                .thenReturn(Mono.just(otp));

        StepVerifier.create(academicVerificationService.verifyOtp(email, otpCode, userId))
                .expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException 
                        && throwable.getMessage().contains("Kode OTP yang Anda masukkan salah"))
                .verify();

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw error when OTP is expired")
    void testVerifyOtpExpired() {
        String email = "student@ui.ac.id";
        String otpCode = "123456";
        Long userId = 1L;

        OtpVerification otp = OtpVerification.builder()
                .id(100L)
                .email(email.toLowerCase())
                .otpCode(otpCode)
                .type("ACADEMIC_VERIFICATION")
                .expiryTime(LocalDateTime.now().minusMinutes(1)) // expired
                .verified(false)
                .build();

        when(otpVerificationRepository.findLatestUnverified(email.toLowerCase(), "ACADEMIC_VERIFICATION"))
                .thenReturn(Mono.just(otp));

        StepVerifier.create(academicVerificationService.verifyOtp(email, otpCode, userId))
                .expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException 
                        && throwable.getMessage().contains("Kode OTP sudah kedaluwarsa"))
                .verify();

        verify(userRepository, never()).save(any());
    }
}
