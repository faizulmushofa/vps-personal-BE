package io.github.faizul.File.core.googleDrive;

import io.github.faizul.User.externalAccount.ExternalAccount;
import io.github.faizul.User.externalAccount.ExternalAccountRepository;
import io.github.faizul.Exception.GoogleDriveNotConnectedException;
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
    private final io.github.faizul.security.jwt.EncryptionService encryptionService;

    public GoogleDriveClient(
            ExternalAccountRepository externalAccountRepository,
            @Value("${google.client-id:${GOOGLE_GLIENT_ID:}}") String clientId,
            @Value("${google.client-secret:${GOOGLE_CLIENT_SECRET:}}") String clientSecret,
            io.github.faizul.security.jwt.EncryptionService encryptionService) {
        this.externalAccountRepository = externalAccountRepository;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.encryptionService = encryptionService;
        io.netty.resolver.DefaultAddressResolverGroup resolver = io.netty.resolver.DefaultAddressResolverGroup.INSTANCE;
        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create().resolver(resolver);
        this.webClient = WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .build();
    }

    public Mono<String> getValidAccessToken(Long externalAccountId) {
        if (externalAccountId == null) {
            return Mono.error(new GoogleDriveNotConnectedException("Akun Google Drive belum dihubungkan. Silakan hubungkan akun Google Anda terlebih dahulu."));
        }
        return externalAccountRepository.findById(externalAccountId)
                .switchIfEmpty(Mono.error(new GoogleDriveNotConnectedException("Akun Google Drive belum dihubungkan. Silakan hubungkan akun Google Anda terlebih dahulu.")))
                .flatMap(account -> {
                    account.setAccessToken(encryptionService.decrypt(account.getAccessToken()));
                    account.setRefreshToken(encryptionService.decrypt(account.getRefreshToken()));
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
            return Mono.error(new GoogleDriveNotConnectedException("Kredensial Google Refresh Token tidak ditemukan. Silakan hubungkan ulang akun Google Anda."));
        }
        return webClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("refresh_token", account.getRefreshToken())
                        .with("client_id", clientId)
                        .with("client_secret", clientSecret)
                        .with("grant_type", "refresh_token"))
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    String newAccessToken = (String) response.get("access_token");
                    Number expiresIn = (Number) response.get("expires_in");
                    long expiresAt = System.currentTimeMillis() + (expiresIn != null ? expiresIn.longValue() * 1000 : 3600000L);

                    account.setAccessToken(encryptionService.encrypt(newAccessToken));
                    account.setRefreshToken(encryptionService.encrypt(account.getRefreshToken()));
                    account.setExpiresAt(expiresAt);
                    return externalAccountRepository.save(account)
                            .thenReturn(newAccessToken);
                });
    }

    public Mono<String> uploadFile(Long externalAccountId, Path filePath, String fileName, String mimeType) {
        return uploadFile(externalAccountId, filePath, fileName, mimeType, null);
    }

    public Mono<String> uploadFile(Long externalAccountId, Path filePath, String fileName, String mimeType, String parentFolderId) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("name", fileName);
        if (parentFolderId != null && !parentFolderId.isBlank()) {
            metadata.put("parents", java.util.List.of(parentFolderId));
        }
        builder.part("metadata", metadata, MediaType.APPLICATION_JSON);
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

    public Flux<byte[]> downloadFileRange(Long externalAccountId, String googleFileId, long start, long end) {
        return getValidAccessToken(externalAccountId)
                .flatMapMany(token -> webClient.get()
                        .uri("https://www.googleapis.com/drive/v3/files/" + googleFileId + "?alt=media")
                        .header("Authorization", "Bearer " + token)
                        .header("Range", "bytes=" + start + "-" + end)
                        .header("Connection", "close")
                        .retrieve()
                        .bodyToFlux(byte[].class)
                        .retryWhen(reactor.util.retry.Retry.backoff(3, java.time.Duration.ofMillis(200))
                                .filter(throwable -> throwable instanceof reactor.netty.http.client.PrematureCloseException ||
                                        (throwable instanceof org.springframework.web.reactive.function.client.WebClientRequestException &&
                                         throwable.getCause() instanceof reactor.netty.http.client.PrematureCloseException)))
                );
    }

    public Mono<String> initiateResumableUpload(Long externalAccountId, String fileName, String mimeType, long totalSize) {
        return initiateResumableUpload(externalAccountId, fileName, mimeType, totalSize, null);
    }

    public Mono<String> initiateResumableUpload(Long externalAccountId, String fileName, String mimeType, long totalSize, String parentFolderId) {
        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("name", fileName);
        if (parentFolderId != null && !parentFolderId.isBlank()) {
            metadata.put("parents", java.util.List.of(parentFolderId));
        }
        return getValidAccessToken(externalAccountId)
                .flatMap(token -> webClient.post()
                        .uri("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Upload-Content-Type", mimeType != null ? mimeType : "application/octet-stream")
                        .header("X-Upload-Content-Length", String.valueOf(totalSize))
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .bodyValue(metadata)
                        .exchangeToMono(response -> {
                            if (response.statusCode().isError()) {
                                return response.bodyToMono(String.class)
                                        .flatMap(body -> Mono.error(new RuntimeException("Failed to initiate resumable upload: " + body)));
                            }
                            String location = response.headers().asHttpHeaders().getFirst("Location");
                            if (location == null) {
                                return Mono.error(new IllegalStateException("Failed to get Google Drive upload session location"));
                            }
                            return Mono.just(location);
                        })
                );
    }

    public Mono<String> uploadChunkResumable(Long externalAccountId, String uploadUrl, Path chunkPath, long start, long end, long totalSize) {
        return getValidAccessToken(externalAccountId)
                .flatMap(token -> {
                    try {
                        java.net.URI uri = new java.net.URI(uploadUrl);
                        String host = uri.getHost();
                        if (host == null || (!host.endsWith("googleapis.com") && !host.endsWith("googleusercontent.com"))) {
                            return Mono.error(new SecurityException("Akses diblokir: Host tujuan tidak diizinkan untuk menghindari SSRF."));
                        }
                    } catch (Exception e) {
                        return Mono.error(new IllegalArgumentException("URL unggah tidak valid."));
                    }

                    FileSystemResource resource = new FileSystemResource(chunkPath);
                    String contentRange = "bytes " + start + "-" + end + "/" + totalSize;

                    return webClient.put()
                            .uri(uploadUrl)
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Range", contentRange)
                            .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                            .bodyValue(resource)
                            .exchangeToMono(res -> {
                                if (res.statusCode().isError()) {
                                    return res.bodyToMono(String.class)
                                            .flatMap(body -> Mono.error(new RuntimeException("GDrive resumable chunk upload failed: " + body)));
                                }
                                if (res.statusCode().value() == 308) {
                                    return Mono.just("");
                                }
                                return res.bodyToMono(Map.class)
                                        .map(body -> (String) body.get("id"));
                            });
                });
    }

    public Mono<String> createFolder(Long externalAccountId, String name, String parentFolderId) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", name);
        body.put("mimeType", "application/vnd.google-apps.folder");
        if (parentFolderId != null && !parentFolderId.isBlank()) {
            body.put("parents", java.util.List.of(parentFolderId));
        } else {
            body.put("parents", java.util.List.of("root"));
        }
        return getValidAccessToken(externalAccountId)
                .flatMap(token -> webClient.post()
                        .uri("https://www.googleapis.com/drive/v3/files")
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(java.util.Map.class)
                        .map(res -> (String) res.get("id"))
                );
    }

    public Mono<java.util.List<java.util.Map<String, Object>>> listFilesAndFolders(Long externalAccountId, String parentFolderId) {
        String parent = parentFolderId != null && !parentFolderId.isBlank() ? parentFolderId : "root";
        String q = "'" + parent + "' in parents and trashed = false";
        return getValidAccessToken(externalAccountId)
                .flatMap(token -> webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("https")
                                .host("www.googleapis.com")
                                .path("/drive/v3/files")
                                .queryParam("q", q)
                                .queryParam("fields", "files(id,name,size,mimeType,createdTime,parents)")
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

    public Mono<Void> moveFile(Long externalAccountId, String fileId, String targetFolderId) {
        String newParent = targetFolderId != null && !targetFolderId.isBlank() ? targetFolderId : "root";
        return getValidAccessToken(externalAccountId)
                .flatMap(token -> {
                    // 1. Dapatkan parent lama dari file
                    return webClient.get()
                            .uri("https://www.googleapis.com/drive/v3/files/" + fileId + "?fields=parents")
                            .header("Authorization", "Bearer " + token)
                            .retrieve()
                            .bodyToMono(java.util.Map.class)
                            .flatMap(res -> {
                                java.util.List<String> parents = (java.util.List<String>) res.get("parents");
                                String oldParents = parents != null ? String.join(",", parents) : "";
                                
                                // 2. Jalankan update parent
                                return webClient.patch()
                                        .uri(uriBuilder -> uriBuilder
                                                .scheme("https")
                                                .host("www.googleapis.com")
                                                .path("/drive/v3/files/" + fileId)
                                                .queryParam("addParents", newParent)
                                                .queryParam("removeParents", oldParents)
                                                .build())
                                        .header("Authorization", "Bearer " + token)
                                        .retrieve()
                                        .toBodilessEntity()
                                        .then();
                            });
                });
    }

    public Mono<String> getFileName(Long externalAccountId, String fileId) {
        if (externalAccountId == null) {
            return Mono.just("Google Drive Folder (Tidak Terhubung)");
        }
        return getValidAccessToken(externalAccountId)
                .flatMap(token -> webClient.get()
                        .uri("https://www.googleapis.com/drive/v3/files/" + fileId + "?fields=name")
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(java.util.Map.class)
                        .map(res -> (String) res.get("name"))
                        .defaultIfEmpty("Google Drive Folder")
                )
                .onErrorReturn("Google Drive Folder (Tidak Terhubung)");
    }

    public Mono<java.util.Map<String, Object>> getFileMetadata(Long externalAccountId, String googleFileId) {
        return getValidAccessToken(externalAccountId)
                .flatMap(token -> webClient.get()
                        .uri("https://www.googleapis.com/drive/v3/files/" + googleFileId + "?fields=id,name,size,mimeType,createdTime,parents")
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(java.util.Map.class)
                        .map(res -> (java.util.Map<String, Object>) res)
                );
    }
}

