package io.github.faizul.security.auth.dtos;

public record ResetPasswordRequest(
        String email,
        String otp,
        String newPassword
) {}
