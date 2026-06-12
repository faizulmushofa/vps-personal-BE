package io.github.faizul.User.dtos;

import java.time.LocalDateTime;
import java.util.List;

public record UserDto(
        Long id,
        String username,
        String email,
        String fullName,
        String avatarUrl,
        String phoneNumber,
        Long storageQuota,
        Boolean isActive,
        List<String> roles,
        LocalDateTime createAt,
        LocalDateTime updateAt,
        LocalDateTime deleteAt
) {
}
