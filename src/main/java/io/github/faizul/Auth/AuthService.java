package io.github.faizul.Auth;

import io.github.faizul.Auth.Dtos.*;
import io.github.faizul.Jwt.JwtService;
import io.github.faizul.User.UserRepository;
import io.github.faizul.User.UserService;
import io.github.faizul.UserRole.UserRoleService;
import io.github.faizul.Utils.Mappings.AuthMapper;
import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserService userService;

    public Mono<RegisterResponse> register(RegisterRequest request){

        return userService.createUser(AuthMapper.toUser(request))
                .thenReturn(new RegisterResponse("Register Successfully"));

    }

    public Mono<Response> login(LoginRequest request){
        return userRepository.findByEmail(request.email())
                .flatMap(e -> {
                    if (e == null) {
                        return Mono.error(new UsernameNotFoundException("Invalid email or password"));
                    }
                    if (!passwordEncoder.matches(request.password(), e.getPassword())) {
                        return Mono.error(new UsernameNotFoundException("Invalid email or password"));
                    }

                    String accessToken = jwtService.generateAccessToken(e);

                    return jwtService.generateRefreshToken(e)
                            .map( refreshToken -> new Response(
                                    accessToken,
                                    refreshToken
                            ));


                });
    }

    public Mono<ResponseRefreshInternal> refresh(String refreshToken){
        return jwtService.refresh(refreshToken)
                .map(e -> new ResponseRefreshInternal(
                        "token refreshed",e.getToken()
                ));
    }
}
