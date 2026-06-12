package io.github.faizul.User.dtos;

public record UpdateProfileRequest(
    String fullName,
    String phoneNumber,
    String avatarUrl
) {}
