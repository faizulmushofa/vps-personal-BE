package io.github.faizul.security.auth;

import io.github.faizul.security.auth.dtos.*;

import io.github.faizul.User.*;
import io.github.faizul.security.auth.dtos.*;

import io.github.faizul.security.auth.dtos.RegisterRequest;
import io.github.faizul.User.User;

public class AuthMapper {

    public static User toUser(RegisterRequest request){
        return User.builder()
                .email(request.email())
                .username(request.username())
                .password(request.password())
                .build();
    }
}
