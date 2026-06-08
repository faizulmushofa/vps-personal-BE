package io.github.faizul.User.externalAccount.ExternalProvider;

import io.github.faizul.User.externalAccount.ExternalAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class GoogleProvider implements ExternalProvider {

    private final String clientId;
    private final WebClient webClient;

    public GoogleProvider(
            @Value("${google.client-id:${GOOGLE_GLIENT_ID:}}") String clientId) {
        this.clientId = clientId;
        this.webClient = WebClient.create();
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
    public Mono<ExternalAccount> exchangeCode(String idToken) {
        return webClient.get()
                .uri("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(map -> {
                    String aud = (String) map.get("aud");
                    if (aud == null || !aud.equals(clientId)) {
                        return Mono.error(new IllegalArgumentException("Invalid ID Token audience"));
                    }

                    ExternalAccount account = ExternalAccount.builder()
                            .provider("GOOGLE")
                            .providerUserId((String) map.get("sub"))
                            .email((String) map.get("email"))
                            .accessToken(idToken)
                            .build();

                    Object expObj = map.get("exp");
                    if (expObj != null) {
                        if (expObj instanceof Number number) {
                            account.setExpiresAt(number.longValue() * 1000);
                        } else {
                            try {
                                account.setExpiresAt(Long.parseLong(expObj.toString()) * 1000);
                            } catch (NumberFormatException e) {
                                // ignore
                            }
                        }
                    }

                    return Mono.just(account);
                });
    }
}
