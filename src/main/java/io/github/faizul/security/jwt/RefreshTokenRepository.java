package io.github.faizul.security.jwt;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface RefreshTokenRepository extends R2dbcRepository<RefreshToken,Long> {
    Mono<RefreshToken> findByToken(String token);
}
