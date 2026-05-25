package io.github.faizul.security.auth.dtos;

public record Response(
        String accessToken,
        String refreshToken
) {
}
