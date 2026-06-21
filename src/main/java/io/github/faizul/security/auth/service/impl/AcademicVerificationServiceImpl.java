package io.github.faizul.security.auth.service.impl;

import io.github.faizul.notification.service.NotificationService;
import io.github.faizul.security.auth.otp.OtpVerification;
import io.github.faizul.security.auth.otp.OtpVerificationRepository;
import io.github.faizul.security.auth.service.AcademicVerificationService;
import io.github.faizul.user.model.AcademicDomain;
import io.github.faizul.user.repository.AcademicDomainRepository;
import io.github.faizul.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AcademicVerificationServiceImpl implements AcademicVerificationService {

    private final UserRepository userRepository;
    private final AcademicDomainRepository academicDomainRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    private final NotificationService notificationService;

    private static final String TYPE_ACADEMIC_VERIFICATION = "ACADEMIC_VERIFICATION";

    @Override
    public Mono<Void> sendOtp(String emailKampus, Long userId) {
        if (emailKampus == null || emailKampus.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Email kampus tidak boleh kosong"));
        }

        String normalizedEmail = emailKampus.trim().toLowerCase();

        // 1. Check uniqueness: academic_email must not already be in use
        return userRepository.existsByAcademicEmail(normalizedEmail)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("Email akademik ini sudah digunakan oleh akun lain."));
                    }

                    // 2. Validate email domain matches registered domains in academic_domains table
                    return academicDomainRepository.findAll()
                            .map(AcademicDomain::getDomain)
                            .collectList()
                            .flatMap(domains -> {
                                boolean isValid = domains.stream().anyMatch(domain -> 
                                        normalizedEmail.endsWith("." + domain.toLowerCase()) || 
                                        normalizedEmail.endsWith("@" + domain.toLowerCase())
                                );
                                if (!isValid) {
                                    return Mono.error(new IllegalArgumentException("Domain email tidak terdaftar sebagai institusi akademik resmi (.ac.id / .edu)."));
                                }

                                // 3. Generate OTP (6-digits)
                                String otpCode = String.format("%06d", new Random().nextInt(1000000));
                                LocalDateTime expiry = LocalDateTime.now().plusMinutes(5);

                                // 4. Invalidate all previous unverified academic OTPs for this email
                                return otpVerificationRepository.invalidateAllUnverified(normalizedEmail, TYPE_ACADEMIC_VERIFICATION)
                                        .then(Mono.defer(() -> {
                                            OtpVerification otp = OtpVerification.builder()
                                                    .email(normalizedEmail)
                                                    .otpCode(otpCode)
                                                    .type(TYPE_ACADEMIC_VERIFICATION)
                                                    .expiryTime(expiry)
                                                    .verified(false)
                                                    .build();
                                            return otpVerificationRepository.save(otp);
                                        }))
                                        .flatMap(savedOtp -> {
                                            String subject = "Horizon Cloud: Verifikasi Email Akademik";
                                            String body = "Halo,\n\n" +
                                                    "Kode OTP untuk verifikasi email akademik Anda adalah: " + otpCode + "\n" +
                                                    "Kode ini berlaku selama 5 menit. Tolong jangan sebarkan kode ini kepada siapa pun.\n\n" +
                                                    "Terima kasih,\n" +
                                                    "Tim Horizon Cloud";
                                            return notificationService.sendNotification(normalizedEmail, subject, body);
                                        });
                            });
                });
    }

    @Override
    public Mono<Void> verifyOtp(String emailKampus, String otpCode, Long userId) {
        if (emailKampus == null || emailKampus.trim().isEmpty() || otpCode == null || otpCode.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Email dan kode OTP tidak boleh kosong"));
        }

        String normalizedEmail = emailKampus.trim().toLowerCase();

        return otpVerificationRepository.findLatestUnverified(normalizedEmail, TYPE_ACADEMIC_VERIFICATION)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Kode OTP tidak ditemukan atau sudah digunakan.")))
                .flatMap(otp -> {
                    if (otp.getExpiryTime().isBefore(LocalDateTime.now())) {
                        return Mono.error(new IllegalArgumentException("Kode OTP sudah kedaluwarsa. Silakan minta kode baru."));
                    }
                    if (!otp.getOtpCode().equals(otpCode.trim())) {
                        return Mono.error(new IllegalArgumentException("Kode OTP yang Anda masukkan salah."));
                    }

                    // OTP is valid!
                    return otpVerificationRepository.invalidateAllUnverified(normalizedEmail, TYPE_ACADEMIC_VERIFICATION)
                            .then(userRepository.findById(userId))
                            .switchIfEmpty(Mono.error(new java.util.NoSuchElementException("User tidak ditemukan.")))
                            .flatMap(user -> {
                                user.setAcademicEmail(normalizedEmail);
                                user.setStudentVerified(true);
                                user.setUpdatedAt(LocalDateTime.now());
                                return userRepository.save(user);
                            })
                            .then();
                });
    }
}
