package io.github.faizul.User.subscription;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface SubscriptionRequestRepository extends ReactiveCrudRepository<SubscriptionRequest, Long> {
    Mono<Boolean> existsByUserIdAndStatus(Long userId, String status);
    Mono<SubscriptionRequest> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    Flux<SubscriptionRequest> findAllByStatus(String status);
}
