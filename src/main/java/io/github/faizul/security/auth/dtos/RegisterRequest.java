package io.github.faizul.security.auth.dtos;

import io.github.faizul.security.auth.dtos.*;

import io.github.faizul.security.auth.*;

public record RegisterRequest(
        String username,
        String email,
        String password
) {
}
