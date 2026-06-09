package io.github.faizul.User.externalAccount;

import io.github.faizul.User.dtos.ExternalAccountDto;
import io.github.faizul.User.externalAccount.ExternalProvider.ExternalProviderFactory;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.File.core.FileRepository;
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
    private final FileRepository fileRepository;

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
        return currentUserContext.getUserId()
                .flatMapMany(userId -> externalAccountRepository.findAllByUserId(userId))
                .map(account -> new ExternalAccountDto(
                        account.getId(),
                        account.getProvider(),
                        account.getEmail()
                ));
    }

    @Override
    public Mono<Void> disconnect(Long externalAccountId) {
        return currentUserContext.getUserId()
                .flatMap(userId -> externalAccountRepository.findById(externalAccountId)
                        .flatMap(account -> {
                            String provider = account.getProvider();
                            String fileProvider = "GOOGLE".equalsIgnoreCase(provider) ? "GOOGLE_DRIVE" : provider;
                            return fileRepository.deleteByUserIdAndProvider(userId, fileProvider)
                                    .then(externalAccountRepository.deleteById(externalAccountId));
                        })
                );
    }
}
