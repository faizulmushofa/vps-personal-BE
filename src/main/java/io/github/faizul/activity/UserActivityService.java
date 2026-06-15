package io.github.faizul.activity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserActivityService {

    private final UserActivityRepository userActivityRepository;

    public Mono<UserActivity> log(Long userId, String type, String description, ServerWebExchange exchange) {
        String ip = "unknown";
        if (exchange != null && exchange.getRequest().getRemoteAddress() != null) {
            ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        
        UserActivity activity = UserActivity.builder()
                .userId(userId)
                .activityType(type)
                .description(description)
                .ipAddress(ip)
                .createdAt(LocalDateTime.now())
                .build();

        return userActivityRepository.save(activity)
                .doOnSuccess(saved -> log.info("Log aktivitas disimpan: User={}, Tipe={}, Deskripsi={}", userId, type, description))
                .onErrorResume(e -> {
                    log.error("Gagal menyimpan log aktivitas. Error: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    public Flux<UserActivity> getAllActivities(Pageable pageable) {
        return userActivityRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Flux<io.github.faizul.activity.dtos.UserActivityResponse> getAllActivitiesWithUserDetails(Pageable pageable) {
        return userActivityRepository.findAllWithUserDetails(pageable);
    }

    public Mono<Long> countActivities() {
        return userActivityRepository.count();
    }
}
