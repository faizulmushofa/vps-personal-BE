package io.github.faizul.user.model.externalprovider;

import io.github.faizul.user.model.ExternalAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component
public class GoogleProvider implements ExternalProvider {

    private final String clientId;
    private final String clientSecret;
    private final WebClient webClient;

    public GoogleProvider(
            @Value("${google.client-id:${GOOGLE_GLIENT_ID:}}") String clientId,
            @Value("${google.client-secret:${GOOGLE_CLIENT_SECRET:}}") String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        io.netty.resolver.DefaultAddressResolverGroup resolver = io.netty.resolver.DefaultAddressResolverGroup.INSTANCE;
        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create().resolver(resolver);
        this.webClient = WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public String providerName() {
        return "GOOGLE";
    }

    @Override
    public String getAuthUrl() {
        return clientId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<ExternalAccount> exchangeCode(String code) {
        return webClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("code", code)
                        .with("client_id", clientId)
                        .with("client_secret", clientSecret)
                        .with("redirect_uri", "postmessage")
                        .with("grant_type", "authorization_code"))
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(tokenResponse -> {
                    String accessToken = (String) tokenResponse.get("access_token");
                    String refreshToken = (String) tokenResponse.get("refresh_token");
                    Number expiresIn = (Number) tokenResponse.get("expires_in");
                    long expiresAt = System.currentTimeMillis() + (expiresIn != null ? expiresIn.longValue() * 1000 : 3600000L);

                    return webClient.get()
                            .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                            .header("Authorization", "Bearer " + accessToken)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .map(userInfo -> {
                                String sub = (String) userInfo.get("sub");
                                String email = (String) userInfo.get("email");

                                return ExternalAccount.builder()
                                        .provider("GOOGLE")
                                        .providerUserId(sub)
                                        .email(email)
                                        .accessToken(accessToken)
                                        .refreshToken(refreshToken)
                                        .expiresAt(expiresAt)
                                        .build();
                            });
                });
    }
}
