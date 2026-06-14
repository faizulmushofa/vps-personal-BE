package io.github.faizul.User.subscription;

import io.github.faizul.User.core.User;
import io.github.faizul.User.core.UserRepository;
import io.github.faizul.User.core.SubscriptionPlanType;
import io.github.faizul.security.role.Role;
import io.github.faizul.security.role.RoleRepository;
import io.github.faizul.security.role.Roles;
import io.github.faizul.security.userrole.UserRole;
import io.github.faizul.security.userrole.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionRequestServiceTest {

    @Mock private SubscriptionRequestRepository subscriptionRequestRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;

    @InjectMocks
    private SubscriptionRequestService subscriptionRequestService;

    private User sampleUser;
    private Role userRole;
    private UserRole sampleUserRole;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .subscriptionTier("FREEMIUM")
                .storageQuota(1073741824L)
                .build();

        userRole = Role.builder().id(1L).name(Roles.USER).build();
        sampleUserRole = UserRole.builder().userId(1L).roleId(1L).build();
    }

    @Test
    @DisplayName("should create request successfully when no pending request exists")
    void createRequest_success() {
        when(subscriptionRequestRepository.existsByUserIdAndStatus(1L, "PENDING"))
                .thenReturn(Mono.just(false));
        when(subscriptionRequestRepository.save(any(SubscriptionRequest.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(subscriptionRequestService.createRequest(1L, "PREMIUM_INDIVIDUAL"))
                .assertNext(req -> {
                    assertThat(req.getUserId()).isEqualTo(1L);
                    assertThat(req.getRequestedTier()).isEqualTo("PREMIUM_INDIVIDUAL");
                    assertThat(req.getStatus()).isEqualTo("PENDING");
                })
                .verifyComplete();

        verify(subscriptionRequestRepository).existsByUserIdAndStatus(1L, "PENDING");
        verify(subscriptionRequestRepository).save(any(SubscriptionRequest.class));
    }

    @Test
    @DisplayName("should throw error when creating request and a pending request already exists")
    void createRequest_alreadyExists() {
        when(subscriptionRequestRepository.existsByUserIdAndStatus(1L, "PENDING"))
                .thenReturn(Mono.just(true));

        StepVerifier.create(subscriptionRequestService.createRequest(1L, "PREMIUM_INDIVIDUAL"))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                        throwable.getMessage().equals("Anda masih memiliki permintaan upgrade yang sedang diproses."))
                .verify();

        verify(subscriptionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw error when requested tier is invalid")
    void createRequest_invalidTier() {
        StepVerifier.create(subscriptionRequestService.createRequest(1L, "FREEMIUM"))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                        throwable.getMessage().equals("Paket tidak valid untuk diajukan"))
                .verify();
    }

    @Test
    @DisplayName("should approve pending request successfully and update user quota")
    void approveRequest_success() {
        SubscriptionRequest request = SubscriptionRequest.builder()
                .id(10L)
                .userId(1L)
                .requestedTier("PREMIUM_INDIVIDUAL")
                .status("PENDING")
                .build();

        when(subscriptionRequestRepository.findById(10L)).thenReturn(Mono.just(request));
        when(subscriptionRequestRepository.save(any(SubscriptionRequest.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepository.findById(1L)).thenReturn(Mono.just(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRoleRepository.findByUserId(1L)).thenReturn(Flux.just(sampleUserRole));
        when(roleRepository.findById(1L)).thenReturn(Mono.just(userRole));

        StepVerifier.create(subscriptionRequestService.approveRequest(10L))
                .assertNext(userDto -> {
                    assertThat(userDto.id()).isEqualTo(1L);
                    assertThat(userDto.subscriptionTier()).isEqualTo("PREMIUM_INDIVIDUAL");
                    assertThat(userDto.storageQuota()).isEqualTo(16106127360L); // 15 GB
                    assertThat(userDto.subscriptionExpiresAt()).isNotNull();
                })
                .verifyComplete();

        assertThat(request.getStatus()).isEqualTo("APPROVED");
        verify(subscriptionRequestRepository).save(request);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("should reject pending request successfully")
    void rejectRequest_success() {
        SubscriptionRequest request = SubscriptionRequest.builder()
                .id(10L)
                .userId(1L)
                .requestedTier("PREMIUM_INDIVIDUAL")
                .status("PENDING")
                .build();

        when(subscriptionRequestRepository.findById(10L)).thenReturn(Mono.just(request));
        when(subscriptionRequestRepository.save(any(SubscriptionRequest.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(subscriptionRequestService.rejectRequest(10L))
                .assertNext(req -> {
                    assertThat(req.getStatus()).isEqualTo("REJECTED");
                })
                .verifyComplete();

        verify(subscriptionRequestRepository).save(request);
    }

    @Test
    @DisplayName("should direct update subscription and set expiresAt null for FREEMIUM")
    void directUpdateSubscription_freemium() {
        sampleUser.setSubscriptionTier("PREMIUM_INDIVIDUAL");
        sampleUser.setStorageQuota(16106127360L);
        sampleUser.setSubscriptionExpiresAt(LocalDateTime.now().plusDays(30));

        when(userRepository.findById(1L)).thenReturn(Mono.just(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRoleRepository.findByUserId(1L)).thenReturn(Flux.just(sampleUserRole));
        when(roleRepository.findById(1L)).thenReturn(Mono.just(userRole));

        StepVerifier.create(subscriptionRequestService.directUpdateSubscription(1L, "FREEMIUM"))
                .assertNext(userDto -> {
                    assertThat(userDto.subscriptionTier()).isEqualTo("FREEMIUM");
                    assertThat(userDto.storageQuota()).isEqualTo(1073741824L); // 1 GB
                    assertThat(userDto.subscriptionExpiresAt()).isNull();
                })
                .verifyComplete();
    }
}
