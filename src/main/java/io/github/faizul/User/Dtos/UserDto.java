package io.github.faizul.User.dtos;

import java.time.LocalDateTime;

public record UserDto(
        String username,
        String email,
        String fullName,
        String avatarUrl,
        String phoneNumber,
        Boolean isActive,
        LocalDateTime createAt,
        LocalDateTime updateAt,
        LocalDateTime deleteAt
) {
}
