package io.github.faizul.File.core.googleDrive;

import io.github.faizul.User.externalAccount.ExternalAccount;
import io.github.faizul.User.externalAccount.ExternalAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.Map;

@Component
public class GoogleDriveClient {

    private final ExternalAccountRepository externalAccountRepository;
    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;

    public GoogleDriveClient(
            ExternalAccountRepository externalAccountRepository,
            @Value("${google.client-id:${GOOGLE_GLIENT_ID:}}") String clientId,
            @Value("${google.client-secret:${GOOGLE_CLIENT_SECRET:}}") String clientSecret) {
        this.externalAccountRepository = externalAccountRepository;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        io.netty.resolver.DefaultAddressResolverGroup resolver = io.netty.resolver.DefaultAddressResolverGroup.INSTANCE;
        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create().resolver(resolver);
        this.webClient = WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .build();
    }

    public Mono<String> getValidAccessToken(Long externalAccountId) {
        return externalAccountRepository.findById(externalAccountId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Akun Google Drive belum dihubungkan. Silakan hubungkan akun Google Anda terlebih dahulu.")))
                .flatMap(account -> {
                    long now = System.currentTimeMillis();
                    // Jika token kadaluarsa atau tersisa kurang dari 5 menit, lakukan refresh
                    if (account.getExpiresAt() == null || now + 300000 > account.getExpiresAt()) {
                        return refreshAccessToken(account);
                    }
                    return Mono.just(account.getAccessToken());
                });
    }

    private Mono<String> refreshAccessToken(ExternalAccount account) {
        if (account.getRefreshToken() == null) {
            return Mono.error(new IllegalStateException("Kredensial Google Refresh Token tidak ditemukan. Silakan hubungkan ulang akun Google Anda."));
        }
        return webClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue("refresh_token=" + account.getRefreshToken() +
                        "&client_id=" + clientId +
                        "&client_secret=" + clientSecret +
                        "&grant_type=refresh_token")
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    String newAccessToken = (String) response.get("access_token");
                    Number expiresIn = (Number) response.get("expires_in");
                    long expiresAt = System.currentTimeMillis() + (expiresIn != null ? expiresIn.longValue() * 1000 : 3600000L);

                    account.setAccessToken(newAccessToken);
                    account.setExpiresAt(expiresAt);
                    return externalAccountRepository.save(account)
                            .thenReturn(newAccessToken);
                });
    }

    public Mono<String> uploadFile(Long externalAccountId, Path filePath, String fileName, String mimeType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("metadata", Map.of("name", fileName), MediaType.APPLICATION_JSON);
        builder.part("media", new FileSystemResource(filePath), MediaType.parseMediaType(mimeType));

        return getValidAccessToken(externalAccountId)
                .flatMap(token -> webClient.post()
                        .uri("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                        .header("Authorization", "Bearer " + token)
                        .body(BodyInserters.fromMultipartData(builder.build()))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(res -> {
                            String id = (String) res.get("id");
                            if (id == null) {
                                throw new IllegalStateException("Gagal mendapatkan ID berkas yang diunggah ke Google Drive");
                            }
                            return id;
                        })
                );
    }

    public Mono<Void> deleteFile(Long externalAccountId, String googleFileId) {
        return getValidAccessToken(externalAccountId)
                .flatMap(token -> webClient.patch()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("https")
                                .host("www.googleapis.com")
                                .path("/drive/v3/files/" + googleFileId)
                                .queryParam("supportsAllDrives", "true")
                                .build())
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .bodyValue(Map.of("trashed", true))
                        .retrieve()
                        .onStatus(status -> status.isError(), response -> 
                            response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        System.err.println("Google Drive Delete (Trash) Error Response: " + body);
                                        return Mono.error(new RuntimeException("Google Drive API error: " + body));
                                    })
                        )
                        .toBodilessEntity()
                        .then()
                );
    }

    public Flux<byte[]> downloadFile(Long externalAccountId, String googleFileId) {
        return getValidAccessToken(externalAccountId)
                .flatMapMany(token -> webClient.get()
                        .uri("https://www.googleapis.com/drive/v3/files/" + googleFileId + "?alt=media")
                        .header("Authorization", "Bearer " + token)
                        .header("Connection", "close")
                        .retrieve()
                        .bodyToFlux(byte[].class)
                        .retryWhen(reactor.util.retry.Retry.backoff(3, java.time.Duration.ofMillis(200))
                                .filter(throwable -> throwable instanceof reactor.netty.http.client.PrematureCloseException ||
                                        (throwable instanceof org.springframework.web.reactive.function.client.WebClientRequestException &&
                                         throwable.getCause() instanceof reactor.netty.http.client.PrematureCloseException)))
                );
    }

    public Mono<java.util.List<java.util.Map<String, Object>>> listFiles(Long externalAccountId) {
        return getValidAccessToken(externalAccountId)
                .flatMap(token -> webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("https")
                                .host("www.googleapis.com")
                                .path("/drive/v3/files")
                                .queryParam("q", "trashed = false and mimeType != 'application/vnd.google-apps.folder'")
                                .queryParam("fields", "files(id,name,size,mimeType,createdTime)")
                                .build())
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(java.util.Map.class)
                        .map(res -> {
                            java.util.List<java.util.Map<String, Object>> files = (java.util.List<java.util.Map<String, Object>>) res.get("files");
                            return files != null ? files : java.util.List.of();
                        })
                );
    }

    public Mono<java.util.Map<String, Object>> getAboutSpace(Long externalAccountId) {
        return getValidAccessToken(externalAccountId)
                .flatMap(token -> webClient.get()
                        .uri("https://www.googleapis.com/drive/v3/about?fields=storageQuota")
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(java.util.Map.class)
                        .map(res -> {
                            java.util.Map<String, Object> quota = (java.util.Map<String, Object>) res.get("storageQuota");
                            return quota != null ? quota : java.util.Map.of();
                        })
                );
    }
}
