package io.github.faizul.Auth;

import io.github.faizul.Auth.Dtos.RegisterRequest;
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
