package io.github.faizul.User.externalAccount.ExternalProvider;

import io.github.faizul.User.externalAccount.ExternalAccount;
import reactor.core.publisher.Mono;

public interface ExternalProvider {
    String providerName();

    String getAuthUrl();

    Mono<ExternalAccount> exchangeCode(String idToken);
}
