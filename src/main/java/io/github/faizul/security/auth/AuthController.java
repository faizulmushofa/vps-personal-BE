package io.github.faizul.security.auth;

import io.github.faizul.security.auth.dtos.*;

import io.github.faizul.security.filter.*;

import io.github.faizul.security.auth.dtos.*;
import io.github.faizul.security.filter.CookieFactory;
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

    // ====== RATE LIMITER: In-memory per-IP tracker (OWASP A07) ======
    private static final int MAX_AUTH_ATTEMPTS = 10;    // max attempts per window
    private static final long WINDOW_MS = 15 * 60 * 1000L; // 15 minutes

    private record RateEntry(AtomicInteger count, long windowStart) {}
    private final Map<String, RateEntry> rateLimitMap = new ConcurrentHashMap<>();

    private boolean isRateLimited(ServerWebExchange exchange) {
        String ip = extractIp(exchange);
        long now = Instant.now().toEpochMilli();

        RateEntry entry = rateLimitMap.compute(ip, (key, existing) -> {
            if (existing == null || now - existing.windowStart() > WINDOW_MS) {
                return new RateEntry(new AtomicInteger(1), now);
            }
            existing.count().incrementAndGet();
            return existing;
        });

        return entry.count().get() > MAX_AUTH_ATTEMPTS;
    }

    private String extractIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        var remoteAddr = exchange.getRequest().getRemoteAddress();
        return remoteAddr != null ? remoteAddr.getAddress().getHostAddress() : "unknown";
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request, ServerWebExchange exchange){
        if (isRateLimited(exchange)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new RegisterResponse("Terlalu banyak percobaan. Silakan coba lagi dalam 15 menit.")));
        }
        return authService.register(request)
                .map(r -> new ResponseEntity<>(r, HttpStatus.OK));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@Valid @RequestBody LoginRequest request, ServerHttpResponse response, ServerWebExchange exchange){
        if (isRateLimited(exchange)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new LoginResponse("Terlalu banyak percobaan login. Silakan coba lagi dalam 15 menit.", null)));
        }
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
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            ServerHttpResponse response
    ){
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
        if (isRateLimited(exchange)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new RegisterResponse("Terlalu banyak percobaan verifikasi. Silakan coba lagi dalam 15 menit.")));
        }
        return authService.verifyRegistration(request)
                .map(r -> new ResponseEntity<>(r, HttpStatus.OK));
    }

    @PostMapping("/forgot-password/request")
    public Mono<ResponseEntity<RegisterResponse>> requestForgotPassword(@Valid @RequestBody ForgotPasswordRequest request, ServerWebExchange exchange) {
        if (isRateLimited(exchange)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new RegisterResponse("Terlalu banyak percobaan. Silakan coba lagi dalam 15 menit.")));
        }
        return authService.requestForgotPassword(request)
                .map(r -> new ResponseEntity<>(r, HttpStatus.OK));
    }

    @PostMapping("/forgot-password/reset")
    public Mono<ResponseEntity<RegisterResponse>> resetPassword(@Valid @RequestBody ResetPasswordRequest request, ServerWebExchange exchange) {
        if (isRateLimited(exchange)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new RegisterResponse("Terlalu banyak percobaan. Silakan coba lagi dalam 15 menit.")));
        }
        return authService.resetPassword(request)
                .map(r -> new ResponseEntity<>(r, HttpStatus.OK));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            ServerHttpResponse response
    ) {
        response.addCookie(CookieFactory.deleteRefreshTokenCookie());
        if (refreshToken == null || refreshToken.isEmpty()) {
            return Mono.just(ResponseEntity.ok().build());
        }
        return authService.logout(refreshToken)
                .thenReturn(ResponseEntity.ok().build());
    }
}
