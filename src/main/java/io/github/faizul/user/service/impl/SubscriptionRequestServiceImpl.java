package io.github.faizul.user.service.impl;

import io.github.faizul.user.model.User;
import io.github.faizul.user.repository.UserRepository;
import io.github.faizul.user.mapper.UserMapper;
import io.github.faizul.user.model.SubscriptionPlanType;
import io.github.faizul.user.dtos.UserDto;
import io.github.faizul.security.userrole.repository.UserRoleRepository;
import io.github.faizul.security.role.repository.RoleRepository;
import io.github.faizul.user.service.SubscriptionRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import io.github.faizul.user.model.SubscriptionRequest;
import io.github.faizul.user.repository.SubscriptionRequestRepository;

import io.github.faizul.payment.service.XenditService;
import io.github.faizul.payment.config.XenditConfig;

@Service
@RequiredArgsConstructor
@Transactional
public class SubscriptionRequestServiceImpl implements SubscriptionRequestService {

    private final SubscriptionRequestRepository subscriptionRequestRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final io.github.faizul.activity.service.UserActivityService userActivityService;
    private final io.github.faizul.security.filter.CurrentUserContext currentUserContext;
    private final XenditService xenditService;
    private final XenditConfig xenditConfig;

    @Override
    public Mono<SubscriptionRequest> createRequest(Long userId, String tier, org.springframework.web.server.ServerWebExchange exchange) {
        String upperTier = tier.toUpperCase();
        if (!upperTier.equals("PREMIUM_INDIVIDUAL") && !upperTier.equals("PREMIUM_ACADEMIC")) {
            return Mono.error(new IllegalArgumentException("Paket tidak valid untuk diajukan"));
        }

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("User tidak ditemukan")))
                .flatMap(user -> {
                    String email = user.getEmail();
                    long amount = upperTier.equals("PREMIUM_INDIVIDUAL") ? 20000L : 15000L;
                    String externalId = "SUB-REQ-" + userId + "-" + System.currentTimeMillis();
                    String description = "Horizon Cloud Upgrade: Paket Langganan " + (upperTier.equals("PREMIUM_INDIVIDUAL") ? "Premium Individual" : "Premium Academic");

                    return subscriptionRequestRepository.existsByUserIdAndStatus(userId, "PENDING")
                            .flatMap(exists -> {
                                if (exists) {
                                    return Mono.error(new IllegalArgumentException("Anda masih memiliki permintaan upgrade yang sedang diproses. Silakan selesaikan pembayaran sebelumnya."));
                                }
                                return xenditService.createInvoice(externalId, amount, email, description)
                                        .flatMap(invoice -> {
                                            String xenditInvoiceId = (String) invoice.get("id");
                                            String invoiceUrl = (String) invoice.get("invoice_url");
                                            String invoiceStatus = (String) invoice.get("status");

                                            SubscriptionRequest request = SubscriptionRequest.builder()
                                                    .userId(userId)
                                                    .requestedTier(upperTier)
                                                    .status("PENDING")
                                                    .xenditInvoiceId(xenditInvoiceId)
                                                    .invoiceUrl(invoiceUrl)
                                                    .externalId(externalId)
                                                    .amount(amount)
                                                    .paymentStatus(invoiceStatus)
                                                    .build();

                                            return subscriptionRequestRepository.save(request)
                                                    .flatMap(savedReq -> userActivityService.log(userId, "CREATE_SUBSCRIPTION_REQUEST", "Mengajukan upgrade paket langganan ke tier: " + tier + " dengan nominal Rp " + amount, exchange)
                                                            .thenReturn(savedReq));
                                        });
                            });
                });
    }

    @Override
    public Mono<SubscriptionRequest> getPendingRequest(Long userId) {
        return subscriptionRequestRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING");
    }

    @Override
    public Flux<SubscriptionRequest> getPendingRequests() {
        return subscriptionRequestRepository.findAllByStatus("PENDING");
    }

    @Override
    public Mono<UserDto> approveRequest(Long requestId, org.springframework.web.server.ServerWebExchange exchange) {
        return subscriptionRequestRepository.findById(requestId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Permintaan upgrade tidak ditemukan")))
                .flatMap(request -> {
                    if (!request.getStatus().equals("PENDING")) {
                        return Mono.error(new IllegalArgumentException("Permintaan upgrade sudah diproses sebelumnya"));
                    }
                    request.setStatus("APPROVED");
                    request.setUpdatedAt(LocalDateTime.now());
                    
                    return subscriptionRequestRepository.save(request)
                            .flatMap(savedRequest -> userRepository.findById(savedRequest.getUserId())
                                    .switchIfEmpty(Mono.error(new NoSuchElementException("User tidak ditemukan")))
                                    .flatMap(user -> {
                                        SubscriptionPlanType plan = SubscriptionPlanType.getPlan(savedRequest.getRequestedTier());
                                        user.setSubscriptionTier(savedRequest.getRequestedTier());
                                        user.setStorageQuota(plan.getLimits().storageQuota());
                                        user.setSubscriptionExpiresAt(LocalDateTime.now().plusDays(30));
                                        user.setAiDailyLimit(plan.getLimits().aiDailyLimit());
                                        user.setMigrationDailyLimit(plan.getLimits().migrationDailyLimit());
                                        user.setMigrationMaxFileSize(plan.getLimits().migrationMaxFileSize());
                                        return userRepository.save(user);
                                    })
                            );
                })
                .flatMap(this::mapToUserDto)
                .flatMap(userDto -> currentUserContext.getUserId()
                        .flatMap(adminId -> userActivityService.log(adminId, "APPROVE_SUBSCRIPTION", "Menyetujui permintaan upgrade paket langganan user ID: " + userDto.id(), exchange)
                                .thenReturn(userDto)));
    }

    @Override
    public Mono<SubscriptionRequest> rejectRequest(Long requestId, org.springframework.web.server.ServerWebExchange exchange) {
        return subscriptionRequestRepository.findById(requestId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Permintaan upgrade tidak ditemukan")))
                .flatMap(request -> {
                    if (!request.getStatus().equals("PENDING")) {
                        return Mono.error(new IllegalArgumentException("Permintaan upgrade sudah diproses sebelumnya"));
                    }
                    request.setStatus("REJECTED");
                    request.setUpdatedAt(LocalDateTime.now());
                    return subscriptionRequestRepository.save(request)
                            .flatMap(savedRequest -> currentUserContext.getUserId()
                                    .flatMap(adminId -> userActivityService.log(adminId, "REJECT_SUBSCRIPTION", "Menolak permintaan upgrade paket langganan request ID: " + requestId, exchange)
                                            .thenReturn(savedRequest)));
                });
    }

    @Override
    public Mono<UserDto> directUpdateSubscription(Long userId, String tier, org.springframework.web.server.ServerWebExchange exchange) {
        String upperTier = tier.toUpperCase();
        SubscriptionPlanType plan = SubscriptionPlanType.getPlan(upperTier);
        
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("User tidak ditemukan")))
                .flatMap(user -> {
                    user.setSubscriptionTier(upperTier);
                    user.setStorageQuota(plan.getLimits().storageQuota());
                    user.setAiDailyLimit(plan.getLimits().aiDailyLimit());
                    user.setMigrationDailyLimit(plan.getLimits().migrationDailyLimit());
                    user.setMigrationMaxFileSize(plan.getLimits().migrationMaxFileSize());
                    if (upperTier.equals("FREEMIUM")) {
                        user.setSubscriptionExpiresAt(null);
                    } else {
                        user.setSubscriptionExpiresAt(LocalDateTime.now().plusDays(30));
                    }
                    return userRepository.save(user);
                })
                .flatMap(this::mapToUserDto)
                .flatMap(userDto -> currentUserContext.getUserId()
                        .flatMap(adminId -> userActivityService.log(adminId, "DIRECT_UPDATE_SUBSCRIPTION", "Mengubah paket langganan secara langsung untuk user ID: " + userId + " ke tier: " + upperTier, exchange)
                                .thenReturn(userDto)));
    }

    private Mono<UserDto> mapToUserDto(User user) {
        return userRoleRepository.findByUserId(user.getId())
                .flatMap(userRole -> roleRepository.findById(userRole.getRoleId()))
                .map(role -> role.getName().name())
                .collectList()
                .map(roles -> UserMapper.UserToDto(user, roles));
    }

    @Override
    public Mono<Void> processXenditWebhook(String callbackTokenHeader, java.util.Map<String, Object> payload, org.springframework.web.server.ServerWebExchange exchange) {
        // 1. Validate Xendit Callback Token
        String configuredToken = xenditConfig.getCallbackToken();
        if (callbackTokenHeader == null || !callbackTokenHeader.equals(configuredToken)) {
            return Mono.error(new org.springframework.security.access.AccessDeniedException("Invalid callback token"));
        }

        // 2. Extract payload info
        String invoiceId = (String) payload.get("id");
        if (invoiceId == null) {
            return Mono.error(new IllegalArgumentException("Invoice ID is missing from payload"));
        }

        // 3. Double Check GET API directly from Xendit
        return xenditService.getInvoice(invoiceId)
                .flatMap(xenditInvoice -> {
                    String actualStatus = (String) xenditInvoice.get("status");
                    String actualExternalId = (String) xenditInvoice.get("external_id");

                    return subscriptionRequestRepository.findByExternalId(actualExternalId)
                            .switchIfEmpty(Mono.error(new NoSuchElementException("Subscription request tidak ditemukan untuk externalId: " + actualExternalId)))
                            .flatMap(request -> {
                                if (!"PENDING".equalsIgnoreCase(request.getStatus())) {
                                    return Mono.empty(); // already processed
                                }

                                request.setPaymentStatus(actualStatus);
                                request.setUpdatedAt(LocalDateTime.now());

                                if ("PAID".equalsIgnoreCase(actualStatus) || "SETTLED".equalsIgnoreCase(actualStatus)) {
                                    request.setStatus("APPROVED");

                                    return subscriptionRequestRepository.save(request)
                                            .flatMap(savedReq -> userRepository.findById(savedReq.getUserId())
                                                    .switchIfEmpty(Mono.error(new NoSuchElementException("User tidak ditemukan")))
                                                    .flatMap(user -> {
                                                        SubscriptionPlanType plan = SubscriptionPlanType.getPlan(savedReq.getRequestedTier());
                                                        user.setSubscriptionTier(savedReq.getRequestedTier());
                                                        user.setStorageQuota(plan.getLimits().storageQuota());
                                                        user.setSubscriptionExpiresAt(LocalDateTime.now().plusDays(30));
                                                        user.setAiDailyLimit(plan.getLimits().aiDailyLimit());
                                                        user.setMigrationDailyLimit(plan.getLimits().migrationDailyLimit());
                                                        user.setMigrationMaxFileSize(plan.getLimits().migrationMaxFileSize());
                                                        return userRepository.save(user);
                                                    })
                                                    .flatMap(user -> userActivityService.log(user.getId(), "APPROVE_SUBSCRIPTION_VIA_PAYMENT", "Upgrade paket langganan berhasil disetujui otomatis melalui pembayaran invoice Xendit: " + invoiceId, exchange))
                                            );
                                } else if ("EXPIRED".equalsIgnoreCase(actualStatus) || "FAILED".equalsIgnoreCase(actualStatus)) {
                                    request.setStatus("REJECTED");
                                    return subscriptionRequestRepository.save(request)
                                            .flatMap(savedReq -> userActivityService.log(savedReq.getUserId(), "SUBSCRIPTION_PAYMENT_FAILED", "Upgrade paket langganan gagal/expired dengan status: " + actualStatus, exchange));
                                } else {
                                    return subscriptionRequestRepository.save(request);
                                }
                            });
                })
                .then();
    }
}
