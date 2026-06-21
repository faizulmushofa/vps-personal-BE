package io.github.faizul.user.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdatePasswordRequest(
    @NotBlank(message = "Password lama wajib diisi")
    String oldPassword,

    @NotBlank(message = "Password baru wajib diisi")
    @Size(min = 8, max = 128, message = "Password harus minimal 8 karakter")
    @Pattern(
            regexp = "^(?=.*[a-zA-Z])(?=.*\\d).{8,}$",
            message = "Password harus minimal 8 karakter dan mengandung kombinasi huruf dan angka"
    )
    String newPassword
) {}
