package io.github.faizul.Jwt;

import io.github.faizul.User.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.secret}")
    private String SECRET;

    public String generateAccessToken(User user){
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("UserId",user.getId())
                .issuedAt(new Date())
                .expiration(Date.from(
                        Instant.now().plus(Duration.ofMinutes(30))
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
                .switchIfEmpty(Mono.error(new RuntimeException("Invalid Token"))
                ).flatMap(existing ->
                        {
                            if (existing.getRevoked()) {
                                return Mono.error(new RuntimeException("Token has been revoked"));
                            }

                            existing.setRevoked(true);

                            RefreshToken newRefreshToken = RefreshToken.builder()
                                    .userId(existing.getUserId())
                                    .token(generateUID())
                                    .createdAt(LocalDateTime.now())
                                    .revoked(false)
                                    .expiredAt(
                                            LocalDateTime.now().plusDays(3)
                                    ).build();
                            return refreshTokenRepository.save(newRefreshToken);
                        });
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
