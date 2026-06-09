package io.github.faizul.security.auth.dtos;

public record RegisterRequest(
        String username,
        String email,
        String password,
        String fullName,
        String phoneNumber
) {
}

