package io.github.faizul.security.auth.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(
        
        @NotBlank(message = "Email wajib diisi")
        @Email(message = "Format email tidak valid")
        String email,

        @NotBlank(message = "Kode OTP wajib diisi")
        @Size(min = 6, max = 6, message = "Kode OTP harus 6 digit")
        String otp
) {}
