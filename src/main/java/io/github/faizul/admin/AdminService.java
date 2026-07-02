package io.github.faizul.admin;

import io.github.faizul.storage.file.repository.FileRepository;
import io.github.faizul.user.repository.UserRepository;
import io.github.faizul.user.model.User;
import io.github.faizul.admin.dtos.AdminUserResponse;
import io.github.faizul.admin.dtos.AiTokenStats;
import io.github.faizul.admin.dtos.TokenHistoryEntry;
import io.github.faizul.admin.dtos.AiTokenLogResponse;
import io.github.faizul.security.role.repository.RoleRepository;
import io.github.faizul.security.userrole.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.faizul.ai.repository.AiTokenLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final FileRepository fileRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final AiTokenLogRepository aiTokenLogRepository;

    public Flux<AdminUserResponse> getAllUsers() {
        return userRepository.findAll()
                .flatMap(user -> {
                    Mono<User> checkDowngradeMono = Mono.just(user);
                    if (user.getSubscriptionExpiresAt() != null && user.getSubscriptionExpiresAt().isBefore(LocalDateTime.now())) {
                        user.setSubscriptionTier("FREEMIUM");
                        user.setStorageQuota(1073741824L);
                        user.setSubscriptionExpiresAt(null);
                        user.setAiDailyLimit(5);
                        user.setMigrationDailyLimit(3);
                        user.setMigrationMaxFileSize(268435456L);
                        checkDowngradeMono = userRepository.save(user);
                    }

                    return checkDowngradeMono.flatMap(activeUser -> {
                        Mono<Long> usedStorageMono = fileRepository.calculateUsedStorageByUserId(activeUser.getId())
                                .defaultIfEmpty(0L);

                        Mono<List<String>> rolesMono = userRoleRepository.findByUserId(activeUser.getId())
                                .flatMap(userRole -> roleRepository.findById(userRole.getRoleId()))
                                .map(role -> role.getName().name())
                                .collectList()
                                .defaultIfEmpty(List.of("USER"));

                        return Mono.zip(usedStorageMono, rolesMono)
                                .map(tuple -> new AdminUserResponse(
                                        activeUser.getId(),
                                        activeUser.getUsername(),
                                        activeUser.getEmail(),
                                        activeUser.getFullName() != null ? activeUser.getFullName() : "",
                                        activeUser.getStorageQuota() != null ? activeUser.getStorageQuota() : 1073741824L,
                                        tuple.getT1(),
                                        activeUser.getIsActive() != null ? activeUser.getIsActive() : true,
                                        activeUser.getAiDailyLimit() != null ? activeUser.getAiDailyLimit() : 5,
                                        activeUser.getDailyAiRequests() != null ? activeUser.getDailyAiRequests() : 0,
                                        tuple.getT2(),
                                        activeUser.getMigrationDailyLimit() != null ? activeUser.getMigrationDailyLimit() : 3,
                                        activeUser.getMigrationMaxFileSize() != null ? activeUser.getMigrationMaxFileSize() : 268435456L,
                                        activeUser.getSubscriptionTier() != null ? activeUser.getSubscriptionTier() : "FREEMIUM",
                                        activeUser.getSubscriptionExpiresAt()
                                ));
                    });
                });
    }

    @Transactional
    public Mono<Void> toggleUserStatus(Long userId, Boolean isActive) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("User dengan ID " + userId + " tidak ditemukan")))
                .flatMap(user -> {
                    user.setIsActive(isActive);
                    return userRepository.save(user);
                })
                .then();
    }

    @Transactional
    public Mono<Void> updateUserAiLimit(Long userId, Integer limit) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("User dengan ID " + userId + " tidak ditemukan")))
                .flatMap(user -> {
                    user.setAiDailyLimit(limit);
                    return userRepository.save(user);
                })
                .then();
    }

    @Transactional
    public Mono<Void> updateUserMigrationLimit(Long userId, Integer limit) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("User dengan ID " + userId + " tidak ditemukan")))
                .flatMap(user -> {
                    user.setMigrationDailyLimit(limit);
                    return userRepository.save(user);
                })
                .then();
    }

    @Transactional
    public Mono<Void> updateUserMigrationMaxSize(Long userId, Long maxSize) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("User dengan ID " + userId + " tidak ditemukan")))
                .flatMap(user -> {
                    user.setMigrationMaxFileSize(maxSize);
                    return userRepository.save(user);
                })
                .then();
    }

    public Mono<AiTokenStats> getAiTokenStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = LocalDateTime.of(today, LocalTime.MIN);
        LocalDateTime monthStart = LocalDateTime.of(today.withDayOfMonth(1), LocalTime.MIN);
        LocalDateTime historyStart = LocalDateTime.of(today.minusDays(6), LocalTime.MIN);

        Mono<AiTokenLogRepository.TokenStatsTuple> todayStatsMono = aiTokenLogRepository.getStatsSince(todayStart)
                .defaultIfEmpty(new AiTokenLogRepository.TokenStatsTuple(0L, 0L, 0L));

        Mono<AiTokenLogRepository.TokenStatsTuple> monthStatsMono = aiTokenLogRepository.getStatsSince(monthStart)
                .defaultIfEmpty(new AiTokenLogRepository.TokenStatsTuple(0L, 0L, 0L));

        Flux<TokenHistoryEntry> historyFlux = aiTokenLogRepository.getHistorySince(historyStart)
                .map(row -> new TokenHistoryEntry(
                        row.logDate() != null ? row.logDate().toString() : "",
                        row.inputTokens(),
                        row.outputTokens(),
                        row.totalTokens()
                ));

        return Mono.zip(todayStatsMono, monthStatsMono, historyFlux.collectList())
                .map(tuple -> {
                    java.util.Map<String, TokenHistoryEntry> mergedMap = new java.util.LinkedHashMap<>();
                    for (int i = 6; i >= 0; i--) {
                        String dateStr = today.minusDays(i).toString();
                        mergedMap.put(dateStr, new TokenHistoryEntry(dateStr, 0L, 0L, 0L));
                    }
                    for (TokenHistoryEntry dbEntry : tuple.getT3()) {
                        mergedMap.put(dbEntry.date(), dbEntry);
                    }
                    return new AiTokenStats(
                            tuple.getT1().inputTokens(),
                            tuple.getT1().outputTokens(),
                            tuple.getT1().totalTokens(),
                            tuple.getT2().inputTokens(),
                            tuple.getT2().outputTokens(),
                            tuple.getT2().totalTokens(),
                            new java.util.ArrayList<>(mergedMap.values())
                    );
                });
    }

    public Flux<AiTokenLogResponse> getAllAiTokenLogs() {
        return aiTokenLogRepository.findAllWithUserDetails();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public Flux<String> exportUsersCsv() {
        String header = "ID,Username,Email,Full Name,Storage Quota (Bytes),Used Storage (Bytes),Is Active,AI Daily Limit,Daily AI Requests,Roles,Migration Daily Limit,Migration Max File Size (Bytes),Subscription Tier,Subscription Expires At\n";
        Flux<String> rows = getAllUsers()
                .map(u -> String.format("%d,%s,%s,%s,%d,%d,%b,%d,%d,%s,%d,%d,%s,%s\n",
                        u.id(),
                        escapeCsv(u.username()),
                        escapeCsv(u.email()),
                        escapeCsv(u.fullName()),
                        u.storageQuota() != null ? u.storageQuota() : 1073741824L,
                        u.usedStorage() != null ? u.usedStorage() : 0L,
                        u.isActive() != null ? u.isActive() : true,
                        u.aiDailyLimit() != null ? u.aiDailyLimit() : 5,
                        u.dailyAiRequests() != null ? u.dailyAiRequests() : 0,
                        escapeCsv(u.roles() != null ? String.join("|", u.roles()) : "USER"),
                        u.migrationDailyLimit() != null ? u.migrationDailyLimit() : 3,
                        u.migrationMaxFileSize() != null ? u.migrationMaxFileSize() : 268435456L,
                        escapeCsv(u.subscriptionTier() != null ? u.subscriptionTier() : "FREEMIUM"),
                        u.subscriptionExpiresAt() != null ? u.subscriptionExpiresAt().toString() : ""
                ));
        return Flux.concat(Flux.just(header), rows);
    }

    public Flux<String> exportAiTokenLogsCsv() {
        String header = "ID,User ID,Username,Email,Activity Type,Provider,Model Name,Input Tokens,Output Tokens,Total Tokens,Created At\n";
        Flux<String> rows = getAllAiTokenLogs()
                .map(l -> String.format("%d,%s,%s,%s,%s,%s,%s,%d,%d,%d,%s\n",
                        l.id(),
                        l.userId() != null ? l.userId().toString() : "",
                        escapeCsv(l.username()),
                        escapeCsv(l.email()),
                        escapeCsv(l.activityType()),
                        escapeCsv(l.provider()),
                        escapeCsv(l.modelName()),
                        l.inputTokens() != null ? l.inputTokens() : 0,
                        l.outputTokens() != null ? l.outputTokens() : 0,
                        l.totalTokens() != null ? l.totalTokens() : 0,
                        l.createdAt() != null ? l.createdAt().toString() : ""
                ));
        return Flux.concat(Flux.just(header), rows);
    }
}
