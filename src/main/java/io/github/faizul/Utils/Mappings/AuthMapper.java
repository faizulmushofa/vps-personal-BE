package io.github.faizul.Utils.Mappings;

import io.github.faizul.Auth.Dtos.RegisterRequest;
import io.github.faizul.Auth.Dtos.RegisterResponse;
import io.github.faizul.User.Dtos.UserDto;
import io.github.faizul.User.User;
import reactor.core.publisher.Mono;

import org.springframework.security.crypto.password.PasswordEncoder;

public class AuthMapper {

    public static User toUser(RegisterRequest request){
        return User.builder()
                .email(request.email())
                .username(request.username())
                .password(request.password())
                .build();
    }
}
