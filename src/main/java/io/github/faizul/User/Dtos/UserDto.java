package io.github.faizul.User.Dtos;

import java.time.LocalDateTime;

public record UserDto(
        String username,
        String email,
        LocalDateTime createAt,
        LocalDateTime updateAt,
        LocalDateTime deleteAt
) {
}
