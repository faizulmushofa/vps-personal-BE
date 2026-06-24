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
import io.github.faizul.user.repository.AcademicDomainRepository;
import io.github.faizul.user.model.AcademicDomain;

import io.github.faizul.payment.service.PaymentService;
import io.github.faizul.payment.config.MidtransConfig;

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
    private final PaymentService paymentService;
    private final MidtransConfig midtransConfig;
    private final AcademicDomainRepository academicDomainRepository;

    @Override
    public Mono<SubscriptionRequest> createRequest(Long userId, String tier, org.springframework.web.server.ServerWebExchange exchange) {
        String upperTier = tier.toUpperCase();
        if (!upperTier.equals("PREMIUM_INDIVIDUAL") && !upperTier.equals("PREMIUM_ACADEMIC")) {
            return Mono.error(new IllegalArgumentException("Paket tidak valid untuk diajukan"));
        }

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("User tidak ditemukan")))
                .flatMap(user -> {
                    if (upperTier.equals("PREMIUM_ACADEMIC")) {
                        return academicDomainRepository.findAll()
                                .map(AcademicDomain::getDomain)
                                .collectList()
                                .flatMap(domains -> {
                                    String email = user.getEmail().toLowerCase();
                                    boolean hasAcademicDomain = domains.stream().anyMatch(domain -> 
                                            email.endsWith("." + domain.toLowerCase()) || 
                                            email.endsWith("@" + domain.toLowerCase())
                                    );
                                    boolean isVerified = Boolean.TRUE.equals(user.getStudentVerified());

                                    if (!isVerified && !hasAcademicDomain) {
                                        return Mono.error(new IllegalArgumentException("Anda harus memverifikasi email akademik terlebih dahulu untuk menikmati paket ini."));
                                    }
                                    return proceedWithSubscriptionRequest(user, upperTier, userId, tier, exchange);
                                });
                    } else {
                        return proceedWithSubscriptionRequest(user, upperTier, userId, tier, exchange);
                    }
                });
    }

    private Mono<SubscriptionRequest> proceedWithSubscriptionRequest(User user, String upperTier, Long userId, String tier, org.springframework.web.server.ServerWebExchange exchange) {
        String email = user.getEmail();
        long amount = upperTier.equals("PREMIUM_INDIVIDUAL") ? 20000L : 15000L;
        String externalId = "SUB-REQ-" + userId + "-" + System.currentTimeMillis();
        String description = "Horizon Cloud Upgrade: Paket Langganan " + (upperTier.equals("PREMIUM_INDIVIDUAL") ? "Premium Individual" : "Premium Academic");

        return subscriptionRequestRepository.existsByUserIdAndStatus(userId, "PENDING")
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("Anda masih memiliki permintaan upgrade yang sedang diproses. Silakan selesaikan pembayaran sebelumnya."));
                    }
                    return paymentService.createPayment(externalId, amount, email, description)
                             .flatMap(transaction -> {
                                 String token = (String) transaction.get("token");
                                 String redirectUrl = (String) transaction.get("redirect_url");

                                 SubscriptionRequest request = SubscriptionRequest.builder()
                                         .userId(userId)
                                         .requestedTier(upperTier)
                                         .status("PENDING")
                                         .xenditInvoiceId(token)
                                         .invoiceUrl(redirectUrl)
                                         .externalId(externalId)
                                         .amount(amount)
                                         .paymentStatus("pending")
                                         .build();

                                 return subscriptionRequestRepository.save(request)
                                         .flatMap(savedReq -> userActivityService.log(userId, "CREATE_SUBSCRIPTION_REQUEST", "Mengajukan upgrade paket langganan ke tier: " + tier + " dengan nominal Rp " + amount, exchange)
                                                 .thenReturn(savedReq));
                             });
                });
    }

    @Override
    public Mono<SubscriptionRequest> getPendingRequest(Long userId) {
        return subscriptionRequestRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING");
    }

    @Override
    public Mono<Void> cancelPendingRequest(Long userId) {
        return subscriptionRequestRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING")
                .flatMap(request -> subscriptionRequestRepository.delete(request));
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

    @Override
    public Mono<Void> processMidtransWebhook(java.util.Map<String, Object> payload, org.springframework.web.server.ServerWebExchange exchange) {
        // 1. Extract values
        String orderId = (String) payload.get("order_id");
        String statusCode = (String) payload.get("status_code");
        String grossAmount = (String) payload.get("gross_amount");
        String signatureKeyFromPayload = (String) payload.get("signature_key");
        String serverKey = midtransConfig.getServerKey();

        if (orderId == null || statusCode == null || grossAmount == null || signatureKeyFromPayload == null) {
            return Mono.error(new IllegalArgumentException("Required Midtrans notification fields are missing"));
        }

        // 2. Validate Signature Key
        String rawString = orderId + statusCode + grossAmount + serverKey;
        String calculatedSignature = hashSha512(rawString);
        if (!calculatedSignature.equalsIgnoreCase(signatureKeyFromPayload)) {
            return Mono.error(new org.springframework.security.access.AccessDeniedException("Invalid Midtrans signature key"));
        }

        // 3. Double Check GET API directly from Midtrans
        return paymentService.getPaymentStatus(orderId)
                .flatMap(midtransStatus -> {
                    String actualStatus = (String) midtransStatus.get("transaction_status");
                    String actualExternalId = (String) midtransStatus.get("order_id");

                    return subscriptionRequestRepository.findByExternalId(actualExternalId)
                            .switchIfEmpty(Mono.error(new NoSuchElementException("Subscription request tidak ditemukan untuk externalId: " + actualExternalId)))
                            .flatMap(request -> {
                                if (!"PENDING".equalsIgnoreCase(request.getStatus())) {
                                    return Mono.empty(); // already processed
                                }

                                request.setPaymentStatus(actualStatus);
                                request.setUpdatedAt(LocalDateTime.now());

                                if ("settlement".equalsIgnoreCase(actualStatus) || "capture".equalsIgnoreCase(actualStatus)) {
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
                                                    .flatMap(user -> userActivityService.log(user.getId(), "APPROVE_SUBSCRIPTION_VIA_PAYMENT", "Upgrade paket langganan berhasil disetujui otomatis melalui pembayaran Midtrans: " + actualExternalId, exchange))
                                            );
                                } else if ("expire".equalsIgnoreCase(actualStatus) || "cancel".equalsIgnoreCase(actualStatus) || "deny".equalsIgnoreCase(actualStatus)) {
                                    request.setStatus("REJECTED");
                                    return subscriptionRequestRepository.save(request)
                                            .flatMap(savedReq -> userActivityService.log(savedReq.getUserId(), "SUBSCRIPTION_PAYMENT_FAILED", "Upgrade paket langganan gagal/expired dengan status Midtrans: " + actualStatus, exchange));
                                } else {
                                    return subscriptionRequestRepository.save(request);
                                }
                            });
                })
                .then();
    }
}
