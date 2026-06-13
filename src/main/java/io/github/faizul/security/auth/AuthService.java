package io.github.faizul.security.auth;

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
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Random;

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

    private String generateOtp() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    public Mono<RegisterResponse> register(RegisterRequest request) {
        String emailNormalized = request.email().toLowerCase().trim();
        return userService.createUser(AuthMapper.toUser(request))
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
                    String emailBody = String.format(
                            "Halo %s,\n\n" +
                            "Terima kasih telah mendaftar di Premium VPS Personal Cloud Storage.\n" +
                            "Kode OTP verifikasi Anda adalah: %s\n\n" +
                            "Kode ini berlaku selama 5 menit. Mohon tidak membagikan kode ini kepada siapa pun.\n\n" +
                            "Salam,\n" +
                            "Tim Cloud Storage",
                            request.fullName(), otpCode
                    );

                    return otpVerificationRepository.save(otp)
                            .flatMap(savedOtp -> notificationService.sendNotification(emailNormalized, emailSubject, emailBody)
                                    .doOnError(err -> log.error("Gagal mengirim email OTP: {}", err.getMessage(), err)))
                            .thenReturn(new RegisterResponse("Register Successfully. Silakan periksa email Anda untuk kode verifikasi OTP."));
                });
    }

    public Mono<RegisterResponse> verifyRegistration(VerifyOtpRequest request) {
        String emailNormalized = request.email().toLowerCase().trim();
        return otpVerificationRepository.findLatestUnverified(emailNormalized, "REGISTRATION")
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Kode OTP tidak valid atau tidak ditemukan")))
                .flatMap(otp -> {
                    if (otp.getExpiryTime().isBefore(LocalDateTime.now())) {
                        return Mono.error(new IllegalArgumentException("Kode OTP telah kadaluarsa"));
                    }

                    if (!otp.getOtpCode().equals(request.otp())) {
                        return Mono.error(new IllegalArgumentException("Kode OTP tidak valid"));
                    }

                    return userRepository.findByEmail(emailNormalized)
                            .switchIfEmpty(Mono.error(new UsernameNotFoundException("User tidak ditemukan")))
                            .flatMap(user -> {
                                user.setIsActive(true);
                                otp.setVerified(true);

                                return userRepository.save(user)
                                        .then(otpVerificationRepository.save(otp))
                                        .thenReturn(new RegisterResponse("Verifikasi email berhasil. Akun Anda telah aktif, silakan login."));
                            });
                });
    }

    public Mono<Response> login(LoginRequest request) {
        String emailNormalized = request.email().toLowerCase().trim();
        return userRepository.findByEmail(emailNormalized)
                .switchIfEmpty(Mono.error(new UsernameNotFoundException("Invalid email or password")))
                .filter(user -> passwordEncoder.matches(request.password(), user.getPassword()))
                .switchIfEmpty(Mono.error(new UsernameNotFoundException("Invalid email or password")))
                .flatMap(user -> {
                    if (!Boolean.TRUE.equals(user.getIsActive())) {
                        return Mono.error(new IllegalArgumentException("Akun Anda belum aktif. Silakan lakukan verifikasi email terlebih dahulu."));
                    }
                    String accessToken = jwtService.generateAccessToken(user);

                    return jwtService.generateRefreshToken(user)
                            .map(refreshToken -> new Response(accessToken, refreshToken));
                });
    }

    public Mono<RegisterResponse> requestForgotPassword(ForgotPasswordRequest request) {
        String emailNormalized = request.email().toLowerCase().trim();
        return userRepository.findByEmail(emailNormalized)
                .switchIfEmpty(Mono.error(new UsernameNotFoundException("Email tidak terdaftar")))
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Akun Anda belum aktif. Silakan verifikasi email terlebih dahulu.")))
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
                    String emailBody = String.format(
                            "Halo %s,\n\n" +
                            "Kami menerima permintaan pemulihan kata sandi untuk akun Anda.\n" +
                            "Kode OTP pemulihan Anda adalah: %s\n\n" +
                            "Kode ini berlaku selama 5 menit. Mohon tidak membagikan kode ini kepada siapa pun.\n\n" +
                            "Jika Anda tidak melakukan permintaan ini, abaikan email ini.\n\n" +
                            "Salam,\n" +
                            "Tim Cloud Storage",
                            user.getFullName() != null ? user.getFullName() : user.getUsername(), otpCode
                    );

                    return otpVerificationRepository.save(otp)
                            .flatMap(savedOtp -> notificationService.sendNotification(emailNormalized, emailSubject, emailBody)
                                    .doOnError(err -> log.error("Gagal mengirim email OTP lupa password: {}", err.getMessage(), err)))
                            .thenReturn(new RegisterResponse("OTP pemulihan kata sandi telah dikirim ke email Anda."));
                });
    }

    public Mono<RegisterResponse> resetPassword(ResetPasswordRequest request) {
        String emailNormalized = request.email().toLowerCase().trim();
        return otpVerificationRepository.findLatestUnverified(emailNormalized, "FORGOT_PASSWORD")
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Kode OTP pemulihan tidak valid atau tidak ditemukan")))
                .flatMap(otp -> {
                    if (otp.getExpiryTime().isBefore(LocalDateTime.now())) {
                        return Mono.error(new IllegalArgumentException("Kode OTP pemulihan telah kadaluarsa"));
                    }

                    if (!otp.getOtpCode().equals(request.otp())) {
                        return Mono.error(new IllegalArgumentException("Kode OTP pemulihan tidak valid"));
                    }

                    return userRepository.findByEmail(emailNormalized)
                            .switchIfEmpty(Mono.error(new UsernameNotFoundException("User tidak ditemukan")))
                            .flatMap(user -> {
                                user.setPassword(passwordEncoder.encode(request.newPassword()));
                                otp.setVerified(true);

                                return userRepository.save(user)
                                        .then(otpVerificationRepository.save(otp))
                                        .thenReturn(new RegisterResponse("Kata sandi berhasil diperbarui. Silakan login kembali."));
                            });
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
                                    "token refreshed"
                            );
                        }));
    }
}
