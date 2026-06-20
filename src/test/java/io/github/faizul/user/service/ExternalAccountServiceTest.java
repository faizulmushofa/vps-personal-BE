package io.github.faizul.user.service;

import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.security.jwt.EncryptionService;
import io.github.faizul.storage.file.repository.FileRepository;
import io.github.faizul.user.model.ExternalAccount;
import io.github.faizul.user.model.User;
import io.github.faizul.user.model.externalprovider.ExternalProvider;
import io.github.faizul.user.model.externalprovider.ExternalProviderFactory;
import io.github.faizul.user.repository.ExternalAccountRepository;
import io.github.faizul.user.repository.UserRepository;
import io.github.faizul.user.service.impl.ExternalAccountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;



@ExtendWith(MockitoExtension.class)
class ExternalAccountServiceTest {

    @Mock private ExternalProviderFactory providerFactory;
    @Mock private ExternalAccountRepository externalAccountRepository;
    @Mock private CurrentUserContext currentUserContext;
    @Mock private FileRepository fileRepository;
    @Mock private ExternalProvider externalProvider;
    @Mock private UserRepository userRepository;
    @Mock private EncryptionService encryptionService;
    @Mock private UserActivityService userActivityService;

    @InjectMocks
    private ExternalAccountServiceImpl externalAccountService;

    private ExternalAccount sampleAccount;

    @BeforeEach
    void setUp() {
        sampleAccount = ExternalAccount.builder()
                .id(1L)
                .userId(10L)
                .provider("GOOGLE")
                .providerUserId("google-sub-123")
                .email("google@example.com")
                .accessToken("access-token-abc")
                .refreshToken("refresh-token-xyz")
                .expiresAt(9999999999L)
                .build();

        lenient().when(userActivityService.log(any(), any(), any(), any())).thenReturn(Mono.empty());
        lenient().when(encryptionService.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(encryptionService.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("getAuthUrl")
    class GetAuthUrlTests {

        @Test
        @DisplayName("should return auth URL for given provider")
        void getAuthUrl_success() {
            when(providerFactory.getProvider("google")).thenReturn(externalProvider);
            when(externalProvider.getAuthUrl()).thenReturn("https://accounts.google.com/o/oauth2/auth?...");

            StepVerifier.create(externalAccountService.getAuthUrl("google"))
                    .assertNext(url -> assertThat(url).startsWith("https://accounts.google.com"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("handleCallback")
    class HandleCallbackTests {

        @Test
        @DisplayName("should save external account on callback")
        void handleCallback_success() {
            User user = User.builder()
                    .id(10L)
                    .username("google@example.com")
                    .email("google@example.com")
                    .isActive(true)
                    .subscriptionTier("FREEMIUM")
                    .storageQuota(1073741824L)
                    .build();
            when(currentUserContext.getUserId()).thenReturn(Mono.just(10L));
            when(userRepository.findById(10L)).thenReturn(Mono.just(user));
            when(fileRepository.calculateUsedStorageByUserId(10L)).thenReturn(Mono.just(0L));
            when(externalAccountRepository.findAllByUserId(10L)).thenReturn(Flux.empty());
            when(providerFactory.getProvider("google")).thenReturn(externalProvider);
            when(externalProvider.exchangeCode("auth-code-123")).thenReturn(Mono.just(sampleAccount));
            when(externalAccountRepository.save(any(ExternalAccount.class))).thenReturn(Mono.just(sampleAccount));

            StepVerifier.create(externalAccountService.handleCallback("google", "auth-code-123", null))
                    .verifyComplete();

            verify(externalAccountRepository).save(argThat(account -> account.getUserId().equals(10L)));
        }
    }

    @Nested
    @DisplayName("getMyAccounts")
    class GetMyAccountsTests {

        @Test
        @DisplayName("should return accounts for current user")
        void getMyAccounts_success() {
            when(currentUserContext.getUserId()).thenReturn(Mono.just(10L));
            when(externalAccountRepository.findAllByUserId(10L)).thenReturn(Flux.just(sampleAccount));

            StepVerifier.create(externalAccountService.getMyAccounts())
                    .assertNext(dto -> {
                        assertThat(dto.id()).isEqualTo(1L);
                        assertThat(dto.provider()).isEqualTo("GOOGLE");
                        assertThat(dto.email()).isEqualTo("google@example.com");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should return empty when user has no accounts")
        void getMyAccounts_empty() {
            when(currentUserContext.getUserId()).thenReturn(Mono.just(10L));
            when(externalAccountRepository.findAllByUserId(10L)).thenReturn(Flux.empty());

            StepVerifier.create(externalAccountService.getMyAccounts())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("disconnect")
    class DisconnectTests {

        @Test
        @DisplayName("should delete files and external account on disconnect")
        void disconnect_success() {
            when(currentUserContext.getUserId()).thenReturn(Mono.just(10L));
            when(externalAccountRepository.findById(1L)).thenReturn(Mono.just(sampleAccount));
            when(fileRepository.deleteByUserIdAndProvider(10L, "GOOGLE_DRIVE")).thenReturn(Mono.empty());
            when(externalAccountRepository.deleteById(1L)).thenReturn(Mono.empty());

            StepVerifier.create(externalAccountService.disconnect(1L, null))
                    .verifyComplete();

            verify(fileRepository).deleteByUserIdAndProvider(10L, "GOOGLE_DRIVE");
            verify(externalAccountRepository).deleteById(1L);
        }
    }
}
