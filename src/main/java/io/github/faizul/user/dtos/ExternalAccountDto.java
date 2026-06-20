package io.github.faizul.user.dtos;

public record ExternalAccountDto(
    Long id,
    String provider,
    String email
) {
}
