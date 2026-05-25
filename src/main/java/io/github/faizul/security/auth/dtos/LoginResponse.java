package io.github.faizul.security.auth.dtos;

import io.github.faizul.security.auth.dtos.*;

import io.github.faizul.security.auth.*;

public record LoginResponse(
        String message,
        String accessToken
) {
}
