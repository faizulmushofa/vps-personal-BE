package io.github.faizul.payment.service;

import reactor.core.publisher.Mono;
import java.util.Map;

public interface XenditService {
    Mono<Map<String, Object>> createInvoice(String externalId, long amount, String payerEmail, String description);
    Mono<Map<String, Object>> getInvoice(String invoiceId);
}
