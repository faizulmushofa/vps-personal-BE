package io.github.faizul.security.auth.controller;

import io.github.faizul.security.auth.service.AcademicVerificationService;
import io.github.faizul.security.filter.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/auth/academic")
@Slf4j
public class AcademicVerificationController {

    private final AcademicVerificationService academicVerificationService;
    private final CurrentUserContext currentUserContext;

    @PostMapping("/send-otp")
    public Mono<ResponseEntity<Map<String, String>>> sendOtp(@RequestParam String emailKampus) {
        return currentUserContext.getUserId()
                .flatMap(userId -> academicVerificationService.sendOtp(emailKampus, userId)
                        .thenReturn(ResponseEntity.ok(Map.of("message", "Kode OTP berhasil dikirim ke email " + emailKampus))))
                .onErrorResume(ex -> {
                    log.error("Gagal mengirim OTP akademik ke {}", emailKampus, ex);
                    return Mono.just(ResponseEntity.badRequest().body(Map.of("message", ex.getMessage())));
                });
    }

    @PostMapping("/verify-otp")
    public Mono<ResponseEntity<Map<String, String>>> verifyOtp(@RequestParam String emailKampus, @RequestParam String otpCode) {
        return currentUserContext.getUserId()
                .flatMap(userId -> academicVerificationService.verifyOtp(emailKampus, otpCode, userId)
                        .thenReturn(ResponseEntity.ok(Map.of("message", "Email akademik berhasil diverifikasi. Anda sekarang memenuhi syarat untuk paket Academic."))))
                .onErrorResume(ex -> {
                    log.error("Gagal memverifikasi OTP akademik untuk {}", emailKampus, ex);
                    return Mono.just(ResponseEntity.badRequest().body(Map.of("message", ex.getMessage())));
                });
    }
}
