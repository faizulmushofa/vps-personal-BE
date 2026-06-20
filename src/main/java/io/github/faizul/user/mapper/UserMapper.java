package io.github.faizul.user.mapper;

import io.github.faizul.user.dtos.UserDto;
import java.util.List;
import io.github.faizul.user.model.User;

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
                .subscriptionTier(userDto.subscriptionTier())
                .subscriptionExpiresAt(userDto.subscriptionExpiresAt())
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
                user.getDeletedAt(),
                user.getSubscriptionTier(),
                user.getSubscriptionExpiresAt()
        );
    }
}
