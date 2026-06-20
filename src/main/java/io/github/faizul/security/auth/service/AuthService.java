package io.github.faizul.security.auth.service;

import io.github.faizul.security.auth.dtos.*;
import io.github.faizul.security.auth.dtos.ForgotPasswordRequest;
import io.github.faizul.security.auth.dtos.LoginRequest;
import io.github.faizul.security.auth.dtos.RegisterRequest;
import io.github.faizul.security.auth.dtos.RegisterResponse;
import io.github.faizul.security.auth.dtos.ResetPasswordRequest;
import io.github.faizul.security.auth.dtos.Response;
import io.github.faizul.security.auth.dtos.ResponseRefreshInternal;
import io.github.faizul.security.auth.dtos.VerifyOtpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


public interface AuthService {
    Mono<RegisterResponse> register(RegisterRequest request, ServerWebExchange exchange);
    Mono<RegisterResponse> verifyRegistration(VerifyOtpRequest request, ServerWebExchange exchange);
    Mono<Response> login(LoginRequest request, ServerWebExchange exchange);
    Mono<Void> logout(String refreshToken, ServerWebExchange exchange);
    Mono<RegisterResponse> requestForgotPassword(ForgotPasswordRequest request, ServerWebExchange exchange);
    Mono<RegisterResponse> resetPassword(ResetPasswordRequest request, ServerWebExchange exchange);
    Mono<RegisterResponse> resendRegistrationOtp(String email, ServerWebExchange exchange);
    Mono<ResponseRefreshInternal> refresh(String refreshToken);
}
