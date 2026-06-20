package io.github.faizul.payment.service;

import reactor.core.publisher.Mono;
import java.util.Map;

public interface PaymentService {
    Mono<Map<String, Object>> createPayment(String externalId, long amount, String payerEmail, String description);
    Mono<Map<String, Object>> getPaymentStatus(String paymentId);
}
