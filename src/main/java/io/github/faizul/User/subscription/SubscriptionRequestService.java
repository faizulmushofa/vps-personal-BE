package io.github.faizul.User.subscription;

import io.github.faizul.User.core.User;
import io.github.faizul.User.core.UserRepository;
import io.github.faizul.User.core.UserMapper;
import io.github.faizul.User.core.SubscriptionPlanType;
import io.github.faizul.User.dtos.UserDto;
import io.github.faizul.security.userrole.UserRoleRepository;
import io.github.faizul.security.role.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional
public class SubscriptionRequestService {

    private final SubscriptionRequestRepository subscriptionRequestRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public Mono<SubscriptionRequest> createRequest(Long userId, String tier) {
        String upperTier = tier.toUpperCase();
        if (!upperTier.equals("PREMIUM_INDIVIDUAL") && !upperTier.equals("PREMIUM_ACADEMIC")) {
            return Mono.error(new IllegalArgumentException("Paket tidak valid untuk diajukan"));
        }

        return subscriptionRequestRepository.existsByUserIdAndStatus(userId, "PENDING")
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("Anda masih memiliki permintaan upgrade yang sedang diproses."));
                    }
                    SubscriptionRequest request = SubscriptionRequest.builder()
                            .userId(userId)
                            .requestedTier(upperTier)
                            .status("PENDING")
                            .build();
                    return subscriptionRequestRepository.save(request);
                });
    }

    public Mono<SubscriptionRequest> getPendingRequest(Long userId) {
        return subscriptionRequestRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING");
    }

    public Flux<SubscriptionRequest> getPendingRequests() {
        return subscriptionRequestRepository.findAllByStatus("PENDING");
    }

    public Mono<UserDto> approveRequest(Long requestId) {
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
                                        return userRepository.save(user);
                                    })
                            );
                })
                .flatMap(this::mapToUserDto);
    }

    public Mono<SubscriptionRequest> rejectRequest(Long requestId) {
        return subscriptionRequestRepository.findById(requestId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Permintaan upgrade tidak ditemukan")))
                .flatMap(request -> {
                    if (!request.getStatus().equals("PENDING")) {
                        return Mono.error(new IllegalArgumentException("Permintaan upgrade sudah diproses sebelumnya"));
                    }
                    request.setStatus("REJECTED");
                    request.setUpdatedAt(LocalDateTime.now());
                    return subscriptionRequestRepository.save(request);
                });
    }

    public Mono<UserDto> directUpdateSubscription(Long userId, String tier) {
        String upperTier = tier.toUpperCase();
        SubscriptionPlanType plan = SubscriptionPlanType.getPlan(upperTier);
        
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("User tidak ditemukan")))
                .flatMap(user -> {
                    user.setSubscriptionTier(upperTier);
                    user.setStorageQuota(plan.getLimits().storageQuota());
                    if (upperTier.equals("FREEMIUM")) {
                        user.setSubscriptionExpiresAt(null);
                    } else {
                        user.setSubscriptionExpiresAt(LocalDateTime.now().plusDays(30));
                    }
                    return userRepository.save(user);
                })
                .flatMap(this::mapToUserDto);
    }

    private Mono<UserDto> mapToUserDto(User user) {
        return userRoleRepository.findByUserId(user.getId())
                .flatMap(userRole -> roleRepository.findById(userRole.getRoleId()))
                .map(role -> role.getName().name())
                .collectList()
                .map(roles -> UserMapper.UserToDto(user, roles));
    }
}
