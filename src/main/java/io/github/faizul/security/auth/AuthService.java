package io.github.faizul.security.auth;

import io.github.faizul.activity.UserActivityService;
import io.github.faizul.notification.EmailTemplateFactory;
import io.github.faizul.security.auth.dtos.*;
import io.github.faizul.security.jwt.JwtService;
import io.github.faizul.User.core.UserRepository;
import io.github.faizul.User.core.UserService;
import io.github.faizul.notification.NotificationService;
import io.github.faizul.security.auth.otp.OtpVerification;
import io.github.faizul.security.auth.otp.OtpVerificationRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@AllArgsConstructor
@Slf4j
public class AuthService {
        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final UserService userService;
        private final OtpVerificationRepository otpVerificationRepository;
        private final NotificationService notificationService;
        private final UserActivityService userActivityService;

        // OWASP A04 FIX: Use SecureRandom instead of java.util.Random
        private static final SecureRandom SECURE_RANDOM = new SecureRandom();

        // OWASP A04 FIX: OTP attempt tracking
        private static final int MAX_OTP_ATTEMPTS = 5;
        private static final long OTP_LOCKOUT_MS = 15 * 60 * 1000L; // 15 minutes

        private record OtpAttemptEntry(AtomicInteger count, long windowStart) {
        }

        private static final Map<String, OtpAttemptEntry> otpAttemptMap = new ConcurrentHashMap<>();

        private boolean isOtpLocked(String email) {
                long now = Instant.now().toEpochMilli();
                OtpAttemptEntry entry = otpAttemptMap.get(email);
                if (entry == null)
                        return false;
                if (now - entry.windowStart() > OTP_LOCKOUT_MS) {
                        otpAttemptMap.remove(email);
                        return false;
                }
                return entry.count().get() >= MAX_OTP_ATTEMPTS;
        }

        private void recordOtpAttempt(String email) {
                long now = Instant.now().toEpochMilli();
                otpAttemptMap.compute(email, (key, existing) -> {
                        if (existing == null || now - existing.windowStart() > OTP_LOCKOUT_MS) {
                                return new OtpAttemptEntry(new AtomicInteger(1), now);
                        }
                        existing.count().incrementAndGet();
                        return existing;
                });
        }

        private void clearOtpAttempts(String email) {
                otpAttemptMap.remove(email);
        }

        private String generateOtp() {
                int code = 100000 + SECURE_RANDOM.nextInt(900000);
                return String.valueOf(code);
        }

        public Mono<RegisterResponse> register(RegisterRequest request, ServerWebExchange exchange) {
                String emailNormalized = request.email().toLowerCase().trim();
                return userRepository.findByEmail(emailNormalized)
                                .flatMap(existingUser -> {
                                        if (Boolean.TRUE.equals(existingUser.getIsActive())) {
                                                return Mono.<RegisterResponse>error(new IllegalArgumentException("Email already exist"));
                                        }

                                        return otpVerificationRepository.findLatestUnverified(emailNormalized, "REGISTRATION")
                                                        .flatMap(latestOtp -> {
                                                                if (latestOtp.getExpiryTime().isAfter(LocalDateTime.now().plusMinutes(4))) {
                                                                        long waitSeconds = java.time.Duration.between(LocalDateTime.now().plusMinutes(4), latestOtp.getExpiryTime()).toSeconds();
                                                                        if (waitSeconds > 0) {
                                                                                return Mono.<RegisterResponse>error(new IllegalArgumentException(
                                                                                                "Silakan tunggu " + waitSeconds + " detik sebelum mendaftar ulang atau meminta OTP baru."));
                                                                        }
                                                                }
                                                                return Mono.empty();
                                                        })
                                                        .then(Mono.defer(() -> {
                                                                existingUser.setUsername(request.username());
                                                                existingUser.setPassword(passwordEncoder.encode(request.password()));
                                                                existingUser.setFullName(request.fullName());
                                                                existingUser.setPhoneNumber(request.phoneNumber());

                                                                return userRepository.save(existingUser)
                                                                                .flatMap(savedUser -> otpVerificationRepository.invalidateAllUnverified(emailNormalized, "REGISTRATION")
                                                                                                .then(Mono.defer(() -> {
                                                                                                        String otpCode = generateOtp();
                                                                                                        OtpVerification otp = OtpVerification.builder()
                                                                                                                        .email(emailNormalized)
                                                                                                                        .otpCode(otpCode)
                                                                                                                        .type("REGISTRATION")
                                                                                                                        .expiryTime(LocalDateTime.now().plusMinutes(5))
                                                                                                                        .verified(false)
                                                                                                                        .build();

                                                                                                        String emailSubject = "OTP Verifikasi Registrasi - Horizon Cloud";
                                                                                                        String name = savedUser.getFullName() != null && !savedUser.getFullName().isBlank() ? savedUser.getFullName() : savedUser.getUsername();
                                                                                                        String htmlBody = EmailTemplateFactory.getOtpTemplate(
                                                                                                                        "Verifikasi Pendaftaran Akun",
                                                                                                                        String.format("Halo %s, terima kasih telah mendaftar di Premium VPS Personal Cloud Storage. Gunakan kode OTP berikut untuk memverifikasi akun Anda:", name),
                                                                                                                        otpCode);

                                                                                                        return otpVerificationRepository.save(otp)
                                                                                                                        .flatMap(savedOtp -> notificationService.sendNotification(emailNormalized, emailSubject, htmlBody)
                                                                                                                                        .doOnError(err -> log.error("Gagal mengirim email OTP: {}", err.getMessage(), err)))
                                                                                                                        .then(userActivityService.log(savedUser.getId(), "REGISTER_REQUEST", "Pendaftaran akun baru untuk email: " + savedUser.getEmail(), exchange))
                                                                                                                        .thenReturn(new RegisterResponse("Register Successfully. Silakan periksa email Anda untuk kode verifikasi OTP."));
                                                                                                })));
                                                        }));
                                })
                                .switchIfEmpty(Mono.defer(() -> userService.createUser(AuthMapper.toUser(request))
                                                .flatMap(userDto -> {
                                                        String otpCode = generateOtp();
                                                        OtpVerification otp = OtpVerification.builder()
                                                                        .email(emailNormalized)
                                                                        .otpCode(otpCode)
                                                                        .type("REGISTRATION")
                                                                        .expiryTime(LocalDateTime.now().plusMinutes(5))
                                                                        .verified(false)
                                                                        .build();

                                                        String emailSubject = "OTP Verifikasi Registrasi - Horizon Cloud";
                                                        String name = request.fullName() != null && !request.fullName().isBlank() ? request.fullName() : request.username();
                                                        String htmlBody = EmailTemplateFactory.getOtpTemplate(
                                                                        "Verifikasi Pendaftaran Akun",
                                                                        String.format("Halo %s, terima kasih telah mendaftar di Premium VPS Personal Cloud Storage. Gunakan kode OTP berikut untuk memverifikasi akun Anda:", name),
                                                                        otpCode);

                                                        return otpVerificationRepository.save(otp)
                                                                        .flatMap(savedOtp -> notificationService.sendNotification(emailNormalized, emailSubject, htmlBody)
                                                                                        .doOnError(err -> log.error("Gagal mengirim email OTP: {}", err.getMessage(), err)))
                                                                        .then(userActivityService.log(userDto.id(), "REGISTER_REQUEST", "Pendaftaran akun baru untuk email: " + userDto.email(), exchange))
                                                                        .thenReturn(new RegisterResponse("Register Successfully. Silakan periksa email Anda untuk kode verifikasi OTP."));
                                                })));
        }

        public Mono<RegisterResponse> verifyRegistration(VerifyOtpRequest request, ServerWebExchange exchange) {
                String emailNormalized = request.email().toLowerCase().trim();

                // OWASP A04: Check OTP attempt limit
                if (isOtpLocked(emailNormalized)) {
                        return Mono.error(new IllegalArgumentException(
                                        "Terlalu banyak percobaan verifikasi OTP. Silakan coba lagi dalam 15 menit."));
                }

                return otpVerificationRepository.findLatestUnverified(emailNormalized, "REGISTRATION")
                                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                                "Kode OTP tidak valid atau tidak ditemukan")))
                                .flatMap(otp -> {
                                        if (otp.getExpiryTime().isBefore(LocalDateTime.now())) {
                                                return Mono.error(new IllegalArgumentException(
                                                                "Kode OTP telah kadaluarsa"));
                                        }

                                        if (!otp.getOtpCode().equals(request.otp())) {
                                                recordOtpAttempt(emailNormalized);
                                                return Mono.error(new IllegalArgumentException("Kode OTP tidak valid"));
                                        }

                                        // OTP correct — clear attempt counter
                                        clearOtpAttempts(emailNormalized);

                                        return userRepository.findByEmail(emailNormalized)
                                                        .switchIfEmpty(Mono.error(new UsernameNotFoundException(
                                                                        "User tidak ditemukan")))
                                                        .flatMap(user -> {
                                                                user.setIsActive(true);
                                                                otp.setVerified(true);

                                                                return userRepository.save(user)
                                                                                .then(otpVerificationRepository.save(otp))
                                                                                .then(userActivityService.log(user.getId(), "REGISTER_SUCCESS", "Verifikasi email pendaftaran berhasil. Akun aktif.", exchange))
                                                                                .thenReturn(new RegisterResponse(
                                                                                                "Verifikasi email berhasil. Akun Anda telah aktif, silakan login."));
                                                        });
                                });
        }

        public Mono<Response> login(LoginRequest request, ServerWebExchange exchange) {
                String emailNormalized = request.email().toLowerCase().trim();
                return userRepository.findByEmail(emailNormalized)
                                .switchIfEmpty(Mono.error(new UsernameNotFoundException("Invalid email or password")))
                                .filter(user -> passwordEncoder.matches(request.password(), user.getPassword()))
                                .switchIfEmpty(Mono.error(new UsernameNotFoundException("Invalid email or password")))
                                .flatMap(user -> {
                                        if (!Boolean.TRUE.equals(user.getIsActive())) {
                                                return Mono.error(new IllegalArgumentException(
                                                                "Akun Anda belum aktif. Silakan lakukan verifikasi email terlebih dahulu."));
                                        }
                                        String accessToken = jwtService.generateAccessToken(user);

                                        return jwtService.revokeAllUserTokens(user.getId())
                                                         .then(jwtService.generateRefreshToken(user))
                                                         .flatMap(refreshToken -> userActivityService.log(user.getId(), "LOGIN_SUCCESS", "Login berhasil", exchange)
                                                                 .thenReturn(new Response(accessToken, refreshToken)));
                                });
        }

        public Mono<Void> logout(String refreshToken) {
                return jwtService.revokeToken(refreshToken);
        }

        public Mono<RegisterResponse> requestForgotPassword(ForgotPasswordRequest request, ServerWebExchange exchange) {
                String emailNormalized = request.email().toLowerCase().trim();
                return userRepository.findByEmail(emailNormalized)
                                .switchIfEmpty(Mono.error(new UsernameNotFoundException("Email tidak terdaftar")))
                                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                                "Akun Anda belum aktif. Silakan verifikasi email terlebih dahulu.")))
                                .flatMap(user -> {
                                        String otpCode = generateOtp();
                                        OtpVerification otp = OtpVerification.builder()
                                                        .email(emailNormalized)
                                                        .otpCode(otpCode)
                                                        .type("FORGOT_PASSWORD")
                                                        .expiryTime(LocalDateTime.now().plusMinutes(5))
                                                        .verified(false)
                                                        .build();

                                        String emailSubject = "OTP Pemulihan Kata Sandi - Horizon Cloud";
                                        String name = user.getFullName() != null ? user.getFullName()
                                                        : user.getUsername();
                                        String htmlBody = EmailTemplateFactory.getOtpTemplate(
                                                        "Pemulihan Kata Sandi",
                                                        String.format("Halo %s, kami menerima permintaan untuk mengatur ulang kata sandi Anda. Masukkan kode OTP berikut untuk melanjutkan proses pemulihan:",
                                                                        name),
                                                        otpCode);

                                        return otpVerificationRepository.save(otp)
                                                        .flatMap(savedOtp -> notificationService
                                                                        .sendNotification(emailNormalized, emailSubject,
                                                                                        htmlBody)
                                                                        .doOnError(err -> log.error(
                                                                                        "Gagal mengirim email OTP lupa password: {}",
                                                                                        err.getMessage(), err)))
                                                        .then(userActivityService.log(user.getId(), "FORGOT_PASSWORD_REQUEST", "Permintaan OTP lupa kata sandi", exchange))
                                                        .thenReturn(new RegisterResponse(
                                                                        "OTP pemulihan kata sandi telah dikirim ke email Anda."));
                                });
        }

        public Mono<RegisterResponse> resetPassword(ResetPasswordRequest request, ServerWebExchange exchange) {
                String emailNormalized = request.email().toLowerCase().trim();

                // OWASP A04: Check OTP attempt limit
                if (isOtpLocked(emailNormalized)) {
                        return Mono.error(new IllegalArgumentException(
                                        "Terlalu banyak percobaan reset password. Silakan coba lagi dalam 15 menit."));
                }

                return otpVerificationRepository.findLatestUnverified(emailNormalized, "FORGOT_PASSWORD")
                                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                                "Kode OTP pemulihan tidak valid atau tidak ditemukan")))
                                .flatMap(otp -> {
                                        if (otp.getExpiryTime().isBefore(LocalDateTime.now())) {
                                                return Mono.error(new IllegalArgumentException(
                                                                "Kode OTP pemulihan telah kadaluarsa"));
                                        }

                                        if (!otp.getOtpCode().equals(request.otp())) {
                                                recordOtpAttempt(emailNormalized);
                                                return Mono.error(new IllegalArgumentException(
                                                                "Kode OTP pemulihan tidak valid"));
                                        }

                                        // OTP correct — clear attempt counter
                                        clearOtpAttempts(emailNormalized);

                                        return userRepository.findByEmail(emailNormalized)
                                                        .switchIfEmpty(Mono.error(new UsernameNotFoundException(
                                                                        "User tidak ditemukan")))
                                                        .flatMap(user -> {
                                                                user.setPassword(passwordEncoder
                                                                                .encode(request.newPassword()));
                                                                otp.setVerified(true);

                                                                return userRepository.save(user)
                                                                                .then(otpVerificationRepository.save(otp))
                                                                                .then(userActivityService.log(user.getId(), "RESET_PASSWORD_SUCCESS", "Mengatur ulang kata sandi akun berhasil", exchange))
                                                                                .thenReturn(new RegisterResponse(
                                                                                                "Kata sandi berhasil diperbarui. Silakan login kembali."));
                                                        });
                                });
        }

        public Mono<RegisterResponse> resendRegistrationOtp(String email, ServerWebExchange exchange) {
                String emailNormalized = email.toLowerCase().trim();
                return userRepository.findByEmail(emailNormalized)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Email tidak terdaftar")))
                                .flatMap(user -> {
                                        if (Boolean.TRUE.equals(user.getIsActive())) {
                                                return Mono.error(new IllegalArgumentException("Akun Anda sudah aktif. Silakan login."));
                                        }

                                        return otpVerificationRepository.findLatestUnverified(emailNormalized, "REGISTRATION")
                                                        .flatMap(latestOtp -> {
                                                                if (latestOtp.getExpiryTime().isAfter(LocalDateTime.now().plusMinutes(4))) {
                                                                        long waitSeconds = java.time.Duration.between(LocalDateTime.now().plusMinutes(4), latestOtp.getExpiryTime()).toSeconds();
                                                                        if (waitSeconds > 0) {
                                                                                return Mono.<RegisterResponse>error(new IllegalArgumentException(
                                                                                                "Silakan tunggu " + waitSeconds + " detik sebelum mengirim ulang OTP."));
                                                                        }
                                                                }
                                                                return Mono.empty();
                                                        })
                                                        .then(Mono.defer(() -> otpVerificationRepository.invalidateAllUnverified(emailNormalized, "REGISTRATION")
                                                                        .then(Mono.defer(() -> {
                                                                                String otpCode = generateOtp();
                                                                                OtpVerification otp = OtpVerification.builder()
                                                                                                .email(emailNormalized)
                                                                                                .otpCode(otpCode)
                                                                                                .type("REGISTRATION")
                                                                                                .expiryTime(LocalDateTime.now().plusMinutes(5))
                                                                                                .verified(false)
                                                                                                .build();

                                                                                String emailSubject = "OTP Verifikasi Pendaftaran Baru - Horizon Cloud";
                                                                                String name = user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername();
                                                                                String htmlBody = EmailTemplateFactory.getOtpTemplate(
                                                                                                "Verifikasi Ulang Pendaftaran Akun",
                                                                                                String.format("Halo %s, ini adalah kode OTP verifikasi baru Anda. Gunakan kode berikut untuk mengaktifkan akun Anda:", name),
                                                                                                otpCode);

                                                                                return otpVerificationRepository.save(otp)
                                                                                                .flatMap(savedOtp -> notificationService.sendNotification(emailNormalized, emailSubject, htmlBody)
                                                                                                                .doOnError(err -> log.error("Gagal mengirim email OTP ulang: {}", err.getMessage(), err)))
                                                                                                .then(userActivityService.log(user.getId(), "REGISTER_RESEND_OTP", "Mengirim ulang OTP verifikasi registrasi ke: " + emailNormalized, exchange))
                                                                                                .thenReturn(new RegisterResponse("Kode OTP baru berhasil dikirim ke email Anda."));
                                                                        }))));
                                });
        }

        public Mono<ResponseRefreshInternal> refresh(String refreshToken) {
                return jwtService.refresh(refreshToken)
                                .flatMap(newRefreshToken -> userRepository.findById(newRefreshToken.getUserId())
                                                .map(user -> {
                                                        String accessToken = jwtService.generateAccessToken(user);
                                                        return new ResponseRefreshInternal(
                                                                        accessToken,
                                                                        newRefreshToken.getToken(),
                                                                        "token refreshed");
                                                }));
        }
}
