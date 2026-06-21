package io.github.faizul.security.auth.service;

import reactor.core.publisher.Mono;

public interface AcademicVerificationService {
    Mono<Void> sendOtp(String emailKampus, Long userId);
    Mono<Void> verifyOtp(String emailKampus, String otpCode, Long userId);
}
