package io.github.faizul.security.auth.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Email wajib diisi")
        @Email(message = "Format email tidak valid")
        String email,

        @NotBlank(message = "Kode OTP wajib diisi")
        @Size(min = 6, max = 6, message = "Kode OTP harus 6 digit")
        String otp,

        @NotBlank(message = "Password baru wajib diisi")
        @Size(min = 8, max = 128, message = "Password harus minimal 8 karakter")
        @Pattern(
                regexp = "^(?=.*[a-zA-Z])(?=.*\\d).{8,}$",
                message = "Password harus minimal 8 karakter dan mengandung kombinasi huruf dan angka"
        )
        String newPassword
) {}
