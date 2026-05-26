package io.github.faizul.User;

import io.github.faizul.User.dtos.*;

import io.github.faizul.User.dtos.UserDto;
import lombok.RequiredArgsConstructor;

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
