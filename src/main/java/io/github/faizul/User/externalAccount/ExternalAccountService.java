package io.github.faizul.User.externalAccount;

import io.github.faizul.User.dtos.ExternalAccountDto;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface ExternalAccountService {
    Mono<String> getAuthUrl(String provider);

    Mono<Void> handleCallback(String provider, String code, ServerWebExchange exchange);

    Flux<ExternalAccountDto> getMyAccounts();

    Mono<Void> disconnect(Long externalAccountId, ServerWebExchange exchange);
}
