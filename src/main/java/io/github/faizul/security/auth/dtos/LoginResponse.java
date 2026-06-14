package io.github.faizul.security.auth.dtos;

public record LoginResponse(
        String message,
        String accessToken
) {
}
