package io.github.faizul.Auth;

import io.github.faizul.Auth.Dtos.RegisterRequest;
import io.github.faizul.Auth.Dtos.RegisterResponse;
import io.github.faizul.User.UserRoleRepository;
import io.github.faizul.UserRole.UserRepository;
import io.github.faizul.Utils.Mappings.AuthMapper;
import io.github.faizul.Utils.Mappings.UserMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class AuthService {
    private final UserRepository userRepository;

    public Mono<RegisterResponse> register(RegisterRequest request){

        return userRepository.save(AuthMapper.toUser(request))
                .thenReturn(new RegisterResponse("Register Successfully"));

    }
}
