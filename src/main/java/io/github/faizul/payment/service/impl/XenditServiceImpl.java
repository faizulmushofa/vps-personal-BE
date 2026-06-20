package io.github.faizul.payment.service.impl;

import io.github.faizul.payment.service.XenditService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
public class XenditServiceImpl implements XenditService {

    private final WebClient webClient;
    private final String frontendUrl;

    public XenditServiceImpl(
            @Qualifier("xenditWebClient") WebClient webClient,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.webClient = webClient;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public Mono<Map<String, Object>> createInvoice(String externalId, long amount, String payerEmail, String description) {
        Map<String, Object> body = new HashMap<>();
        body.put("external_id", externalId);
        body.put("amount", amount);
        body.put("payer_email", payerEmail);
        body.put("description", description);
        body.put("success_redirect_url", frontendUrl + (frontendUrl.endsWith("/") ? "dashboard" : "/dashboard"));
        body.put("failure_redirect_url", frontendUrl + (frontendUrl.endsWith("/") ? "dashboard" : "/dashboard"));

        return webClient.post()
                .uri("/v2/invoices")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @Override
    public Mono<Map<String, Object>> getInvoice(String invoiceId) {
        return webClient.get()
                .uri("/v2/invoices/" + invoiceId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}
