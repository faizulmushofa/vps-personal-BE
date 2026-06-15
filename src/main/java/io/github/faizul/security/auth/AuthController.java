package io.github.faizul.security.auth;

import io.github.faizul.activity.UserActivityService;
import io.github.faizul.security.auth.dtos.*;
import io.github.faizul.security.filter.CookieFactory;
import io.github.faizul.security.filter.CurrentUserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/auth")
public class AuthController {
    private final AuthService authService;
    private final IpRateLimiter rateLimiter;
    private final CurrentUserContext currentUserContext;
    private final UserActivityService userActivityService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url}")
    private String frontendUrl;

    private boolean isValidOrigin(ServerWebExchange exchange) {
        String origin = exchange.getRequest().getHeaders().getFirst("Origin");
        String referer = exchange.getRequest().getHeaders().getFirst("Referer");
        
        String targetUrl = (origin != null && !origin.isBlank()) ? origin : referer;
        if (targetUrl == null || targetUrl.isBlank()) {
            return false;
        }

        try {
            java.net.URI targetUri = new java.net.URI(targetUrl);
            java.net.URI frontendUri = new java.net.URI(frontendUrl);
            
            String targetHost = targetUri.getHost();
            String frontendHost = frontendUri.getHost();
            
            if (targetHost == null || frontendHost == null) {
                return false;
            }
            
            targetHost = targetHost.toLowerCase();
            frontendHost = frontendHost.toLowerCase();
            
            String cleanTarget = targetHost.startsWith("www.") ? targetHost.substring(4) : targetHost;
            String cleanFrontend = frontendHost.startsWith("www.") ? frontendHost.substring(4) : frontendHost;
            
            return cleanTarget.equals(cleanFrontend) || targetHost.endsWith("." + cleanFrontend);
        } catch (Exception e) {
            return false;
        }
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request, ServerWebExchange exchange){
        if (rateLimiter.isRateLimited(exchange, "register", 5, 15 * 60 * 1000L)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new RegisterResponse("Terlalu banyak percobaan. Silakan coba lagi dalam 15 menit.")));
        }
        return authService.register(request, exchange)
                .map(r -> new ResponseEntity<>(r, HttpStatus.OK));
    }

    @PostMapping("/register/resend-otp")
    public Mono<ResponseEntity<RegisterResponse>> resendRegistrationOtp(@RequestParam String email, ServerWebExchange exchange) {
        if (rateLimiter.isRateLimited(exchange, "resend-otp", 5, 15 * 60 * 1000L)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new RegisterResponse("Terlalu banyak percobaan. Silakan coba lagi dalam 15 menit.")));
        }
        return authService.resendRegistrationOtp(email, exchange)
                .map(r -> new ResponseEntity<>(r, HttpStatus.OK));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@Valid @RequestBody LoginRequest request, ServerHttpResponse response, ServerWebExchange exchange){
        if (rateLimiter.isRateLimited(exchange, "login", 5, 15 * 60 * 1000L)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new LoginResponse("Terlalu banyak percobaan login. Silakan coba lagi dalam 15 menit.", null)));
        }
        return authService.login(request, exchange)
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
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            ServerHttpResponse response,
            ServerWebExchange exchange
    ){
        if (!isValidOrigin(exchange)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        if (refreshToken == null || refreshToken.isEmpty()) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return authService.refresh(refreshToken)
                .map(e -> {
                    response.addCookie(
                            CookieFactory.createRefreshTokenCookie(e.refreshToken())
                    );
                    return ResponseEntity.ok(
                            new RefreshResponse(
                                    e.message(),
                                    e.accessToken()
                            )
                    );
                });
    }

    @PostMapping("/verify-registration")
    public Mono<ResponseEntity<RegisterResponse>> verifyRegistration(@Valid @RequestBody VerifyOtpRequest request, ServerWebExchange exchange) {
        if (rateLimiter.isRateLimited(exchange, "verify-registration", 5, 15 * 60 * 1000L)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new RegisterResponse("Terlalu banyak percobaan verifikasi. Silakan coba lagi dalam 15 menit.")));
        }
        return authService.verifyRegistration(request, exchange)
                .map(r -> new ResponseEntity<>(r, HttpStatus.OK));
    }

    @PostMapping("/forgot-password/request")
    public Mono<ResponseEntity<RegisterResponse>> requestForgotPassword(@Valid @RequestBody ForgotPasswordRequest request, ServerWebExchange exchange) {
        if (rateLimiter.isRateLimited(exchange, "forgot-password-request", 5, 15 * 60 * 1000L)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new RegisterResponse("Terlalu banyak percobaan. Silakan coba lagi dalam 15 menit.")));
        }
        return authService.requestForgotPassword(request, exchange)
                .map(r -> new ResponseEntity<>(r, HttpStatus.OK));
    }

    @PostMapping("/forgot-password/reset")
    public Mono<ResponseEntity<RegisterResponse>> resetPassword(@Valid @RequestBody ResetPasswordRequest request, ServerWebExchange exchange) {
        if (rateLimiter.isRateLimited(exchange, "forgot-password-reset", 5, 15 * 60 * 1000L)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new RegisterResponse("Terlalu banyak percobaan. Silakan coba lagi dalam 15 menit.")));
        }
        return authService.resetPassword(request, exchange)
                .map(r -> new ResponseEntity<>(r, HttpStatus.OK));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            ServerHttpResponse response,
            ServerWebExchange exchange
    ) {
        if (!isValidOrigin(exchange)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        response.addCookie(CookieFactory.deleteRefreshTokenCookie());
        
        Mono<Void> logAndRevoke = currentUserContext.getUserId()
                .flatMap(userId -> {
                    Mono<Void> revokeFlow = refreshToken != null && !refreshToken.isEmpty()
                            ? authService.logout(refreshToken)
                            : Mono.empty();
                    return revokeFlow.then(userActivityService.log(userId, "LOGOUT", "Keluar dari aplikasi", exchange)).then();
                })
                .switchIfEmpty(Mono.defer(() -> refreshToken != null && !refreshToken.isEmpty()
                        ? authService.logout(refreshToken)
                        : Mono.empty()
                ));
        
        return logAndRevoke.thenReturn(ResponseEntity.ok().build());
    }
}
