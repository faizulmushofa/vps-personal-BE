package io.github.faizul.security.auth.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Username wajib diisi")
        @Size(min = 3, max = 50, message = "Username harus antara 3-50 karakter")
        String username,

        @NotBlank(message = "Email wajib diisi")
        @Email(message = "Format email tidak valid")
        String email,

        @NotBlank(message = "Password wajib diisi")
        @Size(min = 8, max = 128, message = "Password harus minimal 8 karakter")
        @Pattern(
                regexp = "^(?=.*[a-zA-Z])(?=.*\\d).{8,}$",
                message = "Password harus minimal 8 karakter dan mengandung kombinasi huruf dan angka"
        )
        String password,

        String fullName,

        String phoneNumber
) {
}
