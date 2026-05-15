package io.github.faizul.Auth.Dtos;

public record RegisterRequest(
        String username,
        String email,
        String password
) {
}
