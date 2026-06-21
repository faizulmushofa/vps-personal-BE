package io.github.faizul.payment.controller;

import io.github.faizul.user.service.SubscriptionRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookController {

    private final SubscriptionRequestService subscriptionRequestService;

    @PostMapping("/webhook")
    public Mono<ResponseEntity<Void>> handleMidtransWebhook(
            @RequestBody Map<String, Object> payload,
            ServerWebExchange exchange) {
        log.info("Received Midtrans webhook callback for order ID: {}", payload.get("order_id"));
        return subscriptionRequestService.processMidtransWebhook(payload, exchange)
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .onErrorResume(ex -> {
                    log.error("Error processing Midtrans webhook", ex);
                    if (ex instanceof org.springframework.security.access.AccessDeniedException) {
                        return Mono.just(ResponseEntity.status(401).build());
                    }
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }
}
