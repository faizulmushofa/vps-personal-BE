package io.github.faizul.activity.repository;

import io.github.faizul.activity.dtos.UserActivityResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import io.github.faizul.activity.model.UserActivity;

@Repository
public interface UserActivityRepository extends ReactiveCrudRepository<UserActivity, Long> {
    Flux<UserActivity> findAllByOrderByCreatedAtDesc();
    Flux<UserActivity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT ua.*, u.username, u.email FROM user_activities ua " +
           "LEFT JOIN users u ON ua.user_id = u.id " +
           "ORDER BY ua.created_at DESC LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}")
    Flux<UserActivityResponse> findAllWithUserDetails(Pageable pageable);
}

