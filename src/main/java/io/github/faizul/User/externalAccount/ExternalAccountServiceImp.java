package io.github.faizul.User.externalAccount;

import io.github.faizul.User.dtos.ExternalAccountDto;
import io.github.faizul.User.externalAccount.ExternalProvider.ExternalProviderFactory;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.File.core.FileRepository;
import io.github.faizul.User.core.User;
import io.github.faizul.User.core.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ExternalAccountServiceImp implements ExternalAccountService {

    private final ExternalProviderFactory providerFactory;
    private final ExternalAccountRepository externalAccountRepository;
    private final CurrentUserContext currentUserContext;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final io.github.faizul.security.jwt.EncryptionService encryptionService;
    private final io.github.faizul.activity.UserActivityService userActivityService;

    @Override
    public Mono<String> getAuthUrl(String provider) {
        return Mono.fromSupplier(() -> providerFactory.getProvider(provider).getAuthUrl());
    }

    @Override
    public Mono<Void> handleCallback(String provider, String code, ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> userRepository.findById(userId)
                        .switchIfEmpty(Mono.error(new java.util.NoSuchElementException("User Not Found")))
                        .flatMap(user -> {
                            Mono<User> activeUserMono = Mono.just(user);
                            if (user.getSubscriptionExpiresAt() != null && user.getSubscriptionExpiresAt().isBefore(java.time.LocalDateTime.now())) {
                                user.setSubscriptionTier("FREEMIUM");
                                user.setStorageQuota(1073741824L);
                                user.setSubscriptionExpiresAt(null);
                                activeUserMono = userRepository.save(user);
                            }
                            return activeUserMono;
                        })
                        .flatMap(user -> fileRepository.calculateUsedStorageByUserId(userId)
                                .defaultIfEmpty(0L)
                                .flatMap(usedStorage -> {
                                    long quota = user.getStorageQuota() != null ? user.getStorageQuota() : 1073741824L;
                                    if (usedStorage > quota) {
                                        return Mono.error(new IllegalArgumentException(
                                                "Kapasitas penyimpanan Anda sudah melebihi batas. Fitur menghubungkan akun baru dinonaktifkan."));
                                    }
                                    return Mono.empty();
                                })
                                .then(externalAccountRepository.findAllByUserId(userId).collectList())
                                .flatMap(accounts -> {
                                    int maxAccounts = user.getSubscriptionPlan().getLimits().maxCloudAccounts();
                                    if (accounts.size() >= maxAccounts) {
                                        return Mono.error(new IllegalArgumentException(
                                                "Batas maksimal akun cloud terhubung (" + maxAccounts + ") telah tercapai untuk paket Anda."));
                                    }
                                    return providerFactory.getProvider(provider)
                                             .exchangeCode(code)
                                             .flatMap(account -> {
                                                 account.setUserId(userId);
                                                 account.setAccessToken(encryptionService.encrypt(account.getAccessToken()));
                                                 account.setRefreshToken(encryptionService.encrypt(account.getRefreshToken()));
                                                 return externalAccountRepository.save(account)
                                                         .flatMap(savedAccount -> userActivityService.log(userId, "CONNECT_EXTERNAL_ACCOUNT", "Menghubungkan akun penyimpanan awan baru: " + savedAccount.getEmail() + " (" + provider + ")", exchange)
                                                                 .thenReturn(savedAccount));
                                             });
                                    })
                        )
                )
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
    public Mono<Void> disconnect(Long externalAccountId, ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> externalAccountRepository.findById(externalAccountId)
                        .flatMap(account -> {
                            String provider = account.getProvider();
                            String fileProvider = "GOOGLE".equalsIgnoreCase(provider) ? "GOOGLE_DRIVE" : provider;
                            return fileRepository.deleteByUserIdAndProvider(userId, fileProvider)
                                    .then(externalAccountRepository.deleteById(externalAccountId))
                                    .then(userActivityService.log(userId, "DISCONNECT_EXTERNAL_ACCOUNT", "Memutuskan akun penyimpanan awan ID: " + externalAccountId, exchange))
                                    .then();
                        })
                );
    }
}
