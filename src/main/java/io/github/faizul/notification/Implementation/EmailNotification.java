package io.github.faizul.notification.Implementation;

import io.github.faizul.notification.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class EmailNotification implements NotificationService {

    private final WebClient webClient;

    @Value("${brevo.api-key}")
    private String apiKey;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    @Value("${brevo.sender.name}")
    private String senderName;

    public EmailNotification() {
        io.netty.resolver.DefaultAddressResolverGroup resolver = io.netty.resolver.DefaultAddressResolverGroup.INSTANCE;
        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create().resolver(resolver);
        this.webClient = WebClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public Mono<Void> sendNotification(String to, String subject, String body) {
        Map<String, Object> requestBody = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", body
        );

        return webClient.post()
                .uri("/smtp/email")
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("accept", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.isError(), response -> 
                    response.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                System.err.println("Brevo API Error Response: " + errorBody);
                                return Mono.error(new RuntimeException("Brevo API error: " + errorBody));
                            })
                )
                .toBodilessEntity()
                .then();
    }
}
