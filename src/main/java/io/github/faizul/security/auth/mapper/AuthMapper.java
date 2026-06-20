package io.github.faizul.security.auth.mapper;

import io.github.faizul.security.auth.dtos.RegisterRequest;
import io.github.faizul.user.model.User;

public class AuthMapper {

    public static User toUser(RegisterRequest request){
        return User.builder()
                .email(request.email().toLowerCase().trim())
                .username(request.username())
                .password(request.password())
                .fullName(request.fullName())
                .phoneNumber(request.phoneNumber())
                .isActive(false)
                .build();
    }
}

