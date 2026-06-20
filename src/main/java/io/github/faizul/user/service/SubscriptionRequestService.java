package io.github.faizul.user.service;

import io.github.faizul.user.dtos.UserDto;
import io.github.faizul.user.model.SubscriptionRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SubscriptionRequestService {
    Mono<SubscriptionRequest> createRequest(Long userId, String tier, org.springframework.web.server.ServerWebExchange exchange);
    Mono<SubscriptionRequest> getPendingRequest(Long userId);
    Flux<SubscriptionRequest> getPendingRequests();
    Mono<UserDto> approveRequest(Long requestId, org.springframework.web.server.ServerWebExchange exchange);
    Mono<SubscriptionRequest> rejectRequest(Long requestId, org.springframework.web.server.ServerWebExchange exchange);
    Mono<UserDto> directUpdateSubscription(Long userId, String tier, org.springframework.web.server.ServerWebExchange exchange);
    Mono<Void> processXenditWebhook(String callbackTokenHeader, java.util.Map<String, Object> payload, org.springframework.web.server.ServerWebExchange exchange);
}
