package io.github.faizul.activity.service.impl;

import io.github.faizul.activity.dtos.UserActivityResponse;
import io.github.faizul.activity.model.UserActivity;
import io.github.faizul.activity.repository.UserActivityRepository;
import io.github.faizul.activity.service.UserActivityService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserActivityServiceImpl implements UserActivityService {

    private final UserActivityRepository userActivityRepository;

    @Override
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

    @Override
    public Flux<UserActivity> getAllActivities(Pageable pageable) {
        return userActivityRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Override
    public Flux<UserActivityResponse> getAllActivitiesWithUserDetails(Pageable pageable) {
        return userActivityRepository.findAllWithUserDetails(pageable);
    }

    @Override
    public Mono<Long> countActivities() {
        return userActivityRepository.count();
    }
}
