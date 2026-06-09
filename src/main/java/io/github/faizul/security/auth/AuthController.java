package io.github.faizul.security.auth;

import io.github.faizul.security.auth.dtos.*;

import io.github.faizul.security.filter.*;

import io.github.faizul.security.auth.dtos.*;
import io.github.faizul.security.filter.CookieFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public Mono<ResponseEntity<RegisterResponse>> register(@RequestBody RegisterRequest request){
        return authService.register(request)
                .map(r -> new ResponseEntity<>(r, HttpStatus.OK));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@RequestBody LoginRequest request, ServerHttpResponse response){
        return authService.login(request)
                .map(e -> {
                    response.addCookie(
                            CookieFactory.createRefreshTokenCookie(e.refreshToken())
                    );
                    return ResponseEntity.ok(
                            new LoginResponse(
                                    "Login Succesfully",
                                    e.accessToken()
                            )
                    );
                });
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<RefreshResponse>> refresh(
            @CookieValue("refreshToken") String refreshToken,
            ServerHttpResponse response
    ){
        return authService.refresh(refreshToken)
                .map(e -> {
                    response.addCookie(
                            CookieFactory.createRefreshTokenCookie(e.Token())
                    );
                    return ResponseEntity.ok(
                            new RefreshResponse(
                                    e.message()
                            )
                    );
                });
    }

    @PostMapping("/verify-registration")
    public Mono<ResponseEntity<RegisterResponse>> verifyRegistration(@RequestBody VerifyOtpRequest request) {
        return authService.verifyRegistration(request)
                .map(r -> new ResponseEntity<>(r, HttpStatus.OK));
    }

    @PostMapping("/forgot-password/request")
    public Mono<ResponseEntity<RegisterResponse>> requestForgotPassword(@RequestBody ForgotPasswordRequest request) {
        return authService.requestForgotPassword(request)
                .map(r -> new ResponseEntity<>(r, HttpStatus.OK));
    }

    @PostMapping("/forgot-password/reset")
    public Mono<ResponseEntity<RegisterResponse>> resetPassword(@RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request)
                .map(r -> new ResponseEntity<>(r, HttpStatus.OK));
    }
}
