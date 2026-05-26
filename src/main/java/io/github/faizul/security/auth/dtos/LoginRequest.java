package io.github.faizul.security.auth.dtos;

import io.github.faizul.security.auth.dtos.*;

import io.github.faizul.security.auth.*;

public record LoginRequest(
        String email,
        String password
) {
}
