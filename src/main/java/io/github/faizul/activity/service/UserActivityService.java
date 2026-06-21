package io.github.faizul.activity.service;

import io.github.faizul.activity.dtos.UserActivityResponse;
import io.github.faizul.activity.model.UserActivity;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserActivityService {
    Mono<UserActivity> log(Long userId, String type, String description, ServerWebExchange exchange);
    Flux<UserActivity> getAllActivities(Pageable pageable);
    Flux<UserActivityResponse> getAllActivitiesWithUserDetails(Pageable pageable);
    Mono<Long> countActivities();
}
