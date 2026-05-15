package io.github.faizul.Auth;

import io.github.faizul.Auth.Dtos.RegisterRequest;
import io.github.faizul.Auth.Dtos.RegisterResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public Mono<RegisterResponse> register(@RequestBody RegisterRequest request){
        return authService.register(request);
    }
}
