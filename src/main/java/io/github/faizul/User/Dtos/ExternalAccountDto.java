package io.github.faizul.User.dtos;

public record ExternalAccountDto(
    Long id,
    String provider,
    String email
) {
}
