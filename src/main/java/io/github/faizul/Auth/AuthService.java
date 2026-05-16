package io.github.faizul.Auth;

import io.github.faizul.Auth.Dtos.*;
import io.github.faizul.Jwt.JwtService;
import io.github.faizul.User.UserRepository;
import io.github.faizul.User.UserService;
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

    public Mono<Response> login(LoginRequest request) {

        return userRepository.findByEmail(request.email())
                .switchIfEmpty(Mono.error(new UsernameNotFoundException("Invalid email or password")))
                .filter(user -> passwordEncoder.matches(request.password(), user.getPassword()))
                .switchIfEmpty(Mono.error(new UsernameNotFoundException("Invalid email or password")))
                .flatMap(user -> {
                    String accessToken = jwtService.generateAccessToken(user);

                    return jwtService.generateRefreshToken(user)
                            .map(refreshToken -> new Response(accessToken, refreshToken));
                });
    }

    public Mono<ResponseRefreshInternal> refresh(String refreshToken){
        return jwtService.refresh(refreshToken)
                .map(e -> new ResponseRefreshInternal(
                        "token refreshed",e.getToken()
                ));
    }
}
