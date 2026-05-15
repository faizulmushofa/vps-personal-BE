package io.github.faizul.Auth.Dtos;

public record Response(
        String accessToken,
        String refreshToken
) {
}
