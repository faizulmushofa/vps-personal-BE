package io.github.faizul.security.auth.dtos;

public record ResponseRefreshInternal(
        String accessToken,
        String refreshToken,
        String message
) {
}
