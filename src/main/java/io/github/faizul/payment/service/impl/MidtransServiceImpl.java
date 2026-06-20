package io.github.faizul.payment.service.impl;

import io.github.faizul.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class MidtransServiceImpl implements PaymentService {

    private final WebClient snapWebClient;
    private final WebClient apiWebClient;

    public MidtransServiceImpl(
            @Qualifier("midtransSnapWebClient") WebClient snapWebClient,
            @Qualifier("midtransApiWebClient") WebClient apiWebClient) {
        this.snapWebClient = snapWebClient;
        this.apiWebClient = apiWebClient;
    }

    @Override
    public Mono<Map<String, Object>> createPayment(String externalId, long amount, String payerEmail, String description) {
        Map<String, Object> transactionDetails = Map.of(
                "order_id", externalId,
                "gross_amount", amount
        );
        Map<String, Object> customerDetails = Map.of(
                "email", payerEmail
        );
        Map<String, Object> body = Map.of(
                "transaction_details", transactionDetails,
                "customer_details", customerDetails
        );

        return snapWebClient.post()
                .uri("/transactions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @Override
    public Mono<Map<String, Object>> getPaymentStatus(String externalId) {
        return apiWebClient.get()
                .uri("/v2/" + externalId + "/status")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}
