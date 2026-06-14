package io.github.faizul.security.jwt;

import io.github.faizul.User.core.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.BadCredentialsException;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${SECRET_KEY}")
    private String SECRET;

    @Value("${jwt.access-token-expiry-hours}")
    private long accessTokenExpiryHours;

    public String generateAccessToken(User user){
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("UserId",user.getId())
                .issuedAt(new Date())
                .expiration(Date.from(
                        Instant.now().plus(Duration.ofHours(accessTokenExpiryHours))
                ))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();
    }

    private String generateUID(){
        return UUID.randomUUID().toString();
    }

    public Mono<String> generateRefreshToken(User user){
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .token(generateUID())
                .revoked(false)
                .createdAt(LocalDateTime.now())
                .expiredAt(
                        LocalDateTime.now().plusDays(3)
                ).build();

        return refreshTokenRepository.save(refreshToken)
                .thenReturn(refreshToken.getToken());

    }

    public Mono<RefreshToken> refresh(String oldToken){
        return refreshTokenRepository.findByToken(oldToken)
                .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid Token"))
                ).flatMap(existing ->
                        {
                            if (existing.getRevoked()) {
                                return Mono.error(new BadCredentialsException("Token has been revoked"));
                            }

                            // OWASP A02 FIX: Check token expiry
                            if (existing.getExpiredAt() != null && existing.getExpiredAt().isBefore(LocalDateTime.now())) {
                                return Mono.error(new BadCredentialsException("Refresh token has expired. Please login again."));
                            }

                            existing.setRevoked(true);
                            return refreshTokenRepository.save(existing)
                                    .flatMap(saved -> {
                                        RefreshToken newRefreshToken = RefreshToken.builder()
                                                .userId(saved.getUserId())
                                                .token(generateUID())
                                                .createdAt(LocalDateTime.now())
                                                .revoked(false)
                                                .expiredAt(
                                                        LocalDateTime.now().plusDays(3)
                                                ).build();
                                        return refreshTokenRepository.save(newRefreshToken);
                                    });
                        });
    }

    public Mono<Void> revokeToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .flatMap(existing -> {
                    existing.setRevoked(true);
                    return refreshTokenRepository.save(existing);
                }).then();
    }

    public Mono<Void> revokeAllUserTokens(Long userId) {
        return refreshTokenRepository.revokeAllByUserId(userId);
    }



    public String extractEmail(String token){
        return getClaims(token).getSubject();
    }
    private Claims getClaims(String token){
        SecretKey secretKey = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.parser()
                .verifyWith(secretKey)
                .build().parseSignedClaims(token)
                .getPayload();
    }
    private boolean isTokenExpired(String token) {
        return extractAllClaims(token)
                .getExpiration()
                .before(new Date());
    }

    private Claims extractAllClaims(String token) {
        SecretKey secretKey = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    public boolean isValid(String token) {

        try {
            return !isTokenExpired(token);

        } catch (Exception e) {
            return false;
        }
    }




}
