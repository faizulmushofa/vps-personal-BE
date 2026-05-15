package io.github.faizul.Utils.Mappings;

import io.github.faizul.User.Dtos.UserDto;
import io.github.faizul.User.User;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@RequiredArgsConstructor
public class UserMapper {

    public static User DtoToUser(UserDto userDto) {
        return  User.builder()
                .email(userDto.email())
                .username(userDto.username())
                .createdAt(userDto.createAt())
                .deletedAt(userDto.deleteAt())
                .updatedAt(userDto.updateAt())
                .build();
    }

    public static UserDto UserToDto(User user) {
        return  new UserDto(
                user.getUsername(),
                user.getEmail(),
                user.getCreatedAt(),
                user.getDeletedAt(),
                user.getUpdatedAt()
        );
    }



}
