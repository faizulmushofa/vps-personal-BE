package io.github.faizul.security.auth.dtos;

public record VerifyOtpRequest(
        String email,
        String otp
) {}
