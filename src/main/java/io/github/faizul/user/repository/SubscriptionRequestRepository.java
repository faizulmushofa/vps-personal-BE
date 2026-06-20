package io.github.faizul.user.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import io.github.faizul.user.model.SubscriptionRequest;

@Repository
public interface SubscriptionRequestRepository extends ReactiveCrudRepository<SubscriptionRequest, Long> {
    Mono<Boolean> existsByUserIdAndStatus(Long userId, String status);
    Mono<SubscriptionRequest> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    Flux<SubscriptionRequest> findAllByStatus(String status);
    Mono<SubscriptionRequest> findByExternalId(String externalId);
}
