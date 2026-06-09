package io.github.faizul.User.externalAccount;

import io.github.faizul.User.dtos.ExternalAccountDto;
import io.github.faizul.User.externalAccount.ExternalProvider.ExternalProviderFactory;
import io.github.faizul.security.filter.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ExternalAccountServiceImp implements ExternalAccountService {

    private final ExternalProviderFactory providerFactory;
    private final ExternalAccountRepository externalAccountRepository;
    private final CurrentUserContext currentUserContext;

    @Override
    public Mono<String> getAuthUrl(String provider) {
        return Mono.fromSupplier(() -> providerFactory.getProvider(provider).getAuthUrl());
    }

    @Override
    public Mono<Void> handleCallback(String provider, String code) {
        return currentUserContext.getUserId()
                .flatMap(userId -> providerFactory.getProvider(provider)
                        .exchangeCode(code)
                        .map(account -> {
                            account.setUserId(userId);
                            return account;
                        }))
                .flatMap(externalAccountRepository::save)
                .then();
    }

    @Override
    public Flux<ExternalAccountDto> getMyAccounts() {
        return Flux.empty();
    }

    @Override
    public Mono<Void> disconnect(Long externalAccountId) {
        return externalAccountRepository.deleteById(externalAccountId);
    }
}
