package io.github.faizul.security.auth.dtos;

public record RefreshResponse(
        String message,
        String accessToken
) {
}
