package io.github.faizul.User.dtos;

import io.github.faizul.User.dtos.*;
import io.github.faizul.User.*;

import java.time.LocalDateTime;

public record UserDto(
        String username,
        String email,
        LocalDateTime createAt,
        LocalDateTime updateAt,
        LocalDateTime deleteAt
) {
}
