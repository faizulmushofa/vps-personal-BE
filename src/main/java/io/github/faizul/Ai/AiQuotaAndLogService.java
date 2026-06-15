package io.github.faizul.Ai;

import io.github.faizul.Ai.client.AiGenerationResult;
import io.github.faizul.User.core.User;
import io.github.faizul.User.core.UserRepository;
import io.github.faizul.File.core.FileRepository;
import io.github.faizul.setting.AppSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiQuotaAndLogService {

    private final UserRepository userRepository;
    private final AppSettingService appSettingService;
    private final AiTokenLogRepository aiTokenLogRepository;
    private final FileRepository fileRepository;

    public Mono<User> checkAndIncrementQuota(Long userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Pengguna tidak ditemukan")))
                .flatMap(user -> {
                    Mono<User> activeUserMono = Mono.just(user);
                    if (user.getSubscriptionExpiresAt() != null && user.getSubscriptionExpiresAt().isBefore(java.time.LocalDateTime.now())) {
                        user.setSubscriptionTier("FREEMIUM");
                        user.setStorageQuota(1073741824L);
                        user.setSubscriptionExpiresAt(null);
                        user.setAiDailyLimit(5);
                        user.setMigrationDailyLimit(3);
                        user.setMigrationMaxFileSize(268435456L);
                        activeUserMono = userRepository.save(user);
                    }
                    return activeUserMono;
                })
                .flatMap(user -> fileRepository.calculateUsedStorageByUserId(userId)
                        .defaultIfEmpty(0L)
                        .flatMap(usedStorage -> {
                            long quota = user.getStorageQuota() != null ? user.getStorageQuota() : 1073741824L;
                            if (usedStorage > quota) {
                                return Mono.error(new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, 
                                        "Kapasitas penyimpanan Anda penuh. Harap upgrade paket Anda untuk menggunakan fitur AI."));
                            }
                            
                            LocalDate today = LocalDate.now();
                            if (user.getLastAiRequestDate() == null || !user.getLastAiRequestDate().isEqual(today)) {
                                user.setDailyAiRequests(0);
                                user.setLastAiRequestDate(today);
                            }

                            int limit = user.getAiDailyLimit() != null ? user.getAiDailyLimit() : user.getSubscriptionPlan().getLimits().aiDailyLimit();
                            if (user.getDailyAiRequests() >= limit) {
                                log.warn("User {} telah mencapai batas request AI harian ({}/{})", userId, user.getDailyAiRequests(), limit);
                                return Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, 
                                        "Batas penggunaan harian AI Anda (" + limit + " request) telah tercapai. Silakan coba lagi besok."));
                            }

                            user.setDailyAiRequests(user.getDailyAiRequests() + 1);
                            return userRepository.save(user)
                                    .doOnSuccess(saved -> log.info("Berhasil menginkremen kuota AI user {}: {}/{}", userId, saved.getDailyAiRequests(), limit));
                        })
                );
    }

    public Mono<Void> logTokenUsage(Long userId, String activityType, String provider, String model, AiGenerationResult result) {
        int total = result.promptTokens() + result.generationTokens();
        AiTokenLog logEntry = AiTokenLog.builder()
                .userId(userId)
                .activityType(activityType)
                .provider(provider)
                .modelName(model)
                .inputTokens(result.promptTokens())
                .outputTokens(result.generationTokens())
                .totalTokens(total)
                .build();

        return aiTokenLogRepository.save(logEntry)
                .doOnSuccess(saved -> log.info("Token log disimpan untuk user {} ({} tokens)", userId, total))
                .then();
    }
}
