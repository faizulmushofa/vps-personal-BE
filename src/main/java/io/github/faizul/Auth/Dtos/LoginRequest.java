package io.github.faizul.Auth.Dtos;

public record LoginRequest(
        String email,
        String password
) {
}
