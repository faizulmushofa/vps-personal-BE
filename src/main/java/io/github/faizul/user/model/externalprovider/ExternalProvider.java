package io.github.faizul.user.model.externalprovider;

import io.github.faizul.user.model.ExternalAccount;
import reactor.core.publisher.Mono;

public interface ExternalProvider {
    String providerName();

    String getAuthUrl();

    Mono<ExternalAccount> exchangeCode(String idToken);
}
