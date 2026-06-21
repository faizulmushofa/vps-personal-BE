package io.github.faizul.user.dtos;

public record UpdateProfileRequest(
    String fullName,
    String phoneNumber,
    String avatarUrl
) {}
