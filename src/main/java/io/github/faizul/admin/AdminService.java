package io.github.faizul.admin;

import io.github.faizul.File.core.FileRepository;
import io.github.faizul.User.core.UserRepository;
import io.github.faizul.User.core.User;
import io.github.faizul.admin.dtos.AdminUserResponse;
import io.github.faizul.admin.dtos.AiTokenStats;
import io.github.faizul.admin.dtos.TokenHistoryEntry;
import io.github.faizul.security.role.RoleRepository;
import io.github.faizul.security.userrole.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
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
    private final DatabaseClient databaseClient;

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

        // Query Today Stats
        String todayQuery = "SELECT COALESCE(SUM(input_tokens), 0) as in_t, COALESCE(SUM(output_tokens), 0) as out_t, COALESCE(SUM(total_tokens), 0) as tot_t FROM ai_token_logs WHERE created_at >= :todayStart";
        
        // Query Month Stats
        String monthQuery = "SELECT COALESCE(SUM(input_tokens), 0) as in_t, COALESCE(SUM(output_tokens), 0) as out_t, COALESCE(SUM(total_tokens), 0) as tot_t FROM ai_token_logs WHERE created_at >= :monthStart";

        // Query History Stats (7 days)
        String historyQuery = "SELECT CAST(created_at AS DATE) as log_date, COALESCE(SUM(input_tokens), 0) as in_t, COALESCE(SUM(output_tokens), 0) as out_t, COALESCE(SUM(total_tokens), 0) as tot_t " +
                "FROM ai_token_logs WHERE created_at >= :historyStart " +
                "GROUP BY CAST(created_at AS DATE) ORDER BY log_date ASC";

        Mono<TokenStatsTuple> todayStatsMono = databaseClient.sql(todayQuery)
                .bind("todayStart", todayStart)
                .map(row -> new TokenStatsTuple(
                        ((Number) row.get("in_t")).longValue(),
                        ((Number) row.get("out_t")).longValue(),
                        ((Number) row.get("tot_t")).longValue()
                ))
                .one()
                .defaultIfEmpty(new TokenStatsTuple(0L, 0L, 0L));

        Mono<TokenStatsTuple> monthStatsMono = databaseClient.sql(monthQuery)
                .bind("monthStart", monthStart)
                .map(row -> new TokenStatsTuple(
                        ((Number) row.get("in_t")).longValue(),
                        ((Number) row.get("out_t")).longValue(),
                        ((Number) row.get("tot_t")).longValue()
                ))
                .one()
                .defaultIfEmpty(new TokenStatsTuple(0L, 0L, 0L));

        Flux<TokenHistoryEntry> historyFlux = databaseClient.sql(historyQuery)
                .bind("historyStart", historyStart)
                .map(row -> new TokenHistoryEntry(
                        row.get("log_date").toString(),
                        ((Number) row.get("in_t")).longValue(),
                        ((Number) row.get("out_t")).longValue(),
                        ((Number) row.get("tot_t")).longValue()
                ))
                .all();

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

    private record TokenStatsTuple(Long inputTokens, Long outputTokens, Long totalTokens) {}
}
