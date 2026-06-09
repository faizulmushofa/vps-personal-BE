package io.github.faizul.User.externalAccount;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ExternalAccountRepository extends R2dbcRepository<ExternalAccount, Long> {
    Mono<ExternalAccount> findByUserIdAndProvider(Long userId, String provider);
    Flux<ExternalAccount> findAllByUserId(Long userId);
}
