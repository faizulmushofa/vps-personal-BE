package io.github.faizul.payment.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
@Getter
public class XenditConfig {

    @Value("${XENDIT_SECRET_KEY:dummy-xendit-secret-key}")
    private String secretKey;

    @Value("${XENDIT_CALLBACK_TOKEN:dummy-xendit-callback-token}")
    private String callbackToken;

    @Value("${XENDIT_BASE_URL:https://api.xendit.co}")
    private String baseUrl;

    @Bean(name = "xenditWebClient")
    public WebClient xenditWebClient() {
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(
                (secretKey + ":").getBytes(StandardCharsets.UTF_8)
        );

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
