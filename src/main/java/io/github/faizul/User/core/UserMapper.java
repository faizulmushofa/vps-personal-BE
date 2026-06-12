package io.github.faizul.User.core;

import io.github.faizul.User.dtos.UserDto;
import java.util.List;

public class UserMapper {

    public static User DtoToUser(UserDto userDto) {
        return User.builder()
                .id(userDto.id())
                .email(userDto.email())
                .username(userDto.username())
                .fullName(userDto.fullName())
                .avatarUrl(userDto.avatarUrl())
                .phoneNumber(userDto.phoneNumber())
                .storageQuota(userDto.storageQuota())
                .isActive(userDto.isActive())
                .createdAt(userDto.createAt())
                .deletedAt(userDto.deleteAt())
                .updatedAt(userDto.updateAt())
                .build();
    }

    public static UserDto UserToDto(User user) {
        return UserToDto(user, List.of());
    }

    public static UserDto UserToDto(User user, List<String> roles) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getPhoneNumber(),
                user.getStorageQuota(),
                user.getIsActive(),
                roles,
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getDeletedAt()
        );
    }
}
