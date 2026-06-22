package io.github.faizul.user.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import io.github.faizul.user.model.ExternalAccount;

public interface ExternalAccountRepository extends R2dbcRepository<ExternalAccount, Long> {
    Flux<ExternalAccount> findByUserIdAndProvider(Long userId, String provider);
    Mono<ExternalAccount> findByIdAndUserId(Long id, Long userId);
    Flux<ExternalAccount> findAllByUserId(Long userId);
}
