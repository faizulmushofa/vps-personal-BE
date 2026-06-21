package io.github.faizul.security.auth.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        
        @NotBlank(message = "Email wajib diisi")
        @Email(message = "Format email tidak valid")
        String email
) {}
