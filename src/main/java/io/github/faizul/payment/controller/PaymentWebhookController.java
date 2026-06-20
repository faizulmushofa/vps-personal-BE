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
    public Mono<ResponseEntity<Void>> handleXenditWebhook(
            @RequestHeader(value = "x-callback-token", required = false) String callbackToken,
            @RequestBody Map<String, Object> payload,
            ServerWebExchange exchange) {
        log.info("Received Xendit webhook callback for invoice ID: {}", payload.get("id"));
        return subscriptionRequestService.processXenditWebhook(callbackToken, payload, exchange)
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .onErrorResume(ex -> {
                    log.error("Error processing Xendit webhook", ex);
                    if (ex instanceof org.springframework.security.access.AccessDeniedException) {
                        return Mono.just(ResponseEntity.status(401).build());
                    }
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }
}
