package io.github.faizul.user.service;

import io.github.faizul.security.role.model.Role;
import io.github.faizul.security.role.model.Roles;
import io.github.faizul.security.role.repository.RoleRepository;
import io.github.faizul.security.userrole.model.UserRole;
import io.github.faizul.security.userrole.repository.UserRoleRepository;
import io.github.faizul.user.model.SubscriptionRequest;
import io.github.faizul.user.model.User;
import io.github.faizul.user.repository.SubscriptionRequestRepository;
import io.github.faizul.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.user.service.impl.SubscriptionRequestServiceImpl;

import io.github.faizul.payment.service.PaymentService;
import io.github.faizul.payment.config.MidtransConfig;

@ExtendWith(MockitoExtension.class)
class SubscriptionRequestServiceTest {

    @Mock private SubscriptionRequestRepository subscriptionRequestRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserActivityService userActivityService;
    @Mock private CurrentUserContext currentUserContext;
    @Mock private PaymentService paymentService;
    @Mock private MidtransConfig midtransConfig;

    @InjectMocks
    private SubscriptionRequestServiceImpl subscriptionRequestService;

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

        lenient().when(userActivityService.log(any(), any(), any(), any())).thenReturn(Mono.empty());
        lenient().when(currentUserContext.getUserId()).thenReturn(Mono.just(1L));
    }

    private String hashSha512(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException("SHA-512 hashing failed", ex);
        }
    }

    @Test
    @DisplayName("should create request successfully when no pending request exists")
    void createRequest_success() {
        when(userRepository.findById(1L)).thenReturn(Mono.just(sampleUser));
        when(subscriptionRequestRepository.existsByUserIdAndStatus(1L, "PENDING"))
                .thenReturn(Mono.just(false));

        java.util.Map<String, Object> fakeInvoice = java.util.Map.of(
            "token", "inv-123",
            "redirect_url", "https://checkout.midtrans.com/web/inv-123"
        );
        when(paymentService.createPayment(any(), anyLong(), any(), any()))
                .thenReturn(Mono.just(fakeInvoice));

        when(subscriptionRequestRepository.save(any(SubscriptionRequest.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(subscriptionRequestService.createRequest(1L, "PREMIUM_INDIVIDUAL", null))
                .assertNext(req -> {
                    assertThat(req.getUserId()).isEqualTo(1L);
                    assertThat(req.getRequestedTier()).isEqualTo("PREMIUM_INDIVIDUAL");
                    assertThat(req.getStatus()).isEqualTo("PENDING");
                    assertThat(req.getXenditInvoiceId()).isEqualTo("inv-123");
                    assertThat(req.getInvoiceUrl()).isEqualTo("https://checkout.midtrans.com/web/inv-123");
                })
                .verifyComplete();

        verify(userRepository).findById(1L);
        verify(subscriptionRequestRepository).existsByUserIdAndStatus(1L, "PENDING");
        verify(subscriptionRequestRepository).save(any(SubscriptionRequest.class));
    }

    @Test
    @DisplayName("should throw error when creating request and a pending request already exists")
    void createRequest_alreadyExists() {
        when(userRepository.findById(1L)).thenReturn(Mono.just(sampleUser));
        when(subscriptionRequestRepository.existsByUserIdAndStatus(1L, "PENDING"))
                .thenReturn(Mono.just(true));

        StepVerifier.create(subscriptionRequestService.createRequest(1L, "PREMIUM_INDIVIDUAL", null))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                        throwable.getMessage().startsWith("Anda masih memiliki permintaan upgrade yang sedang diproses."))
                .verify();

        verify(subscriptionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw error when requested tier is invalid")
    void createRequest_invalidTier() {
        StepVerifier.create(subscriptionRequestService.createRequest(1L, "FREEMIUM", null))
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

        StepVerifier.create(subscriptionRequestService.approveRequest(10L, null))
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

        StepVerifier.create(subscriptionRequestService.rejectRequest(10L, null))
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

        StepVerifier.create(subscriptionRequestService.directUpdateSubscription(1L, "FREEMIUM", null))
                .assertNext(userDto -> {
                    assertThat(userDto.subscriptionTier()).isEqualTo("FREEMIUM");
                    assertThat(userDto.storageQuota()).isEqualTo(1073741824L); // 1 GB
                    assertThat(userDto.subscriptionExpiresAt()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("should process midtrans webhook successfully and upgrade user tier")
    void processMidtransWebhook_success() {
        when(midtransConfig.getServerKey()).thenReturn("dummy-server-key");

        String orderId = "SUB-REQ-1L-123";
        String statusCode = "200";
        String grossAmount = "20000.00";
        String serverKey = "dummy-server-key";

        String signature = hashSha512(orderId + statusCode + grossAmount + serverKey);
        java.util.Map<String, Object> payload = java.util.Map.of(
            "order_id", orderId,
            "status_code", statusCode,
            "gross_amount", grossAmount,
            "signature_key", signature
        );

        java.util.Map<String, Object> midtransStatus = java.util.Map.of(
            "transaction_status", "settlement",
            "order_id", orderId
        );

        SubscriptionRequest request = SubscriptionRequest.builder()
                .id(10L)
                .userId(1L)
                .requestedTier("PREMIUM_INDIVIDUAL")
                .status("PENDING")
                .externalId(orderId)
                .build();

        when(paymentService.getPaymentStatus(orderId)).thenReturn(Mono.just(midtransStatus));
        when(subscriptionRequestRepository.findByExternalId(orderId)).thenReturn(Mono.just(request));
        when(subscriptionRequestRepository.save(any(SubscriptionRequest.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userRepository.findById(1L)).thenReturn(Mono.just(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(subscriptionRequestService.processMidtransWebhook(payload, null))
                .verifyComplete();

        assertThat(request.getStatus()).isEqualTo("APPROVED");
        assertThat(request.getPaymentStatus()).isEqualTo("settlement");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("should fail when signature is invalid")
    void processMidtransWebhook_invalidSignature() {
        when(midtransConfig.getServerKey()).thenReturn("dummy-server-key");

        java.util.Map<String, Object> payload = java.util.Map.of(
            "order_id", "SUB-REQ-1L-123",
            "status_code", "200",
            "gross_amount", "20000.00",
            "signature_key", "invalid-signature"
        );

        StepVerifier.create(subscriptionRequestService.processMidtransWebhook(payload, null))
                .expectError(org.springframework.security.access.AccessDeniedException.class)
                .verify();

        verify(paymentService, never()).getPaymentStatus(any());
    }
}
