package io.github.faizul.User.dtos;

public record UpdatePasswordRequest(
    String oldPassword,
    String newPassword
) {}
