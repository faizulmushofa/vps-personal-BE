package io.github.faizul.File.pdf.llamaparse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
public class LlamaParseClient {

    private final WebClient webClient;

    @Value("${llamaparse.api-key:}")
    private String apiKey;

    @Value("${llamaparse.enabled:true}")
    private boolean enabled;

    @Value("${llamaparse.tier:fast}")
    private String tier;

    public LlamaParseClient() {
        this.webClient = WebClient.builder().baseUrl("https://api.cloud.llamaindex.ai").build();
    }

    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.trim().isEmpty();
    }

    public Mono<String> parsePdf(Path filePath) {
        if (!isEnabled()) {
            return Mono.error(new IllegalStateException("LlamaParse API is not enabled or API key is missing."));
        }

        log.info("Mengunggah PDF ke LlamaParse: {}", filePath.getFileName());
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new FileSystemResource(filePath.toFile()));

        return webClient.post()
                .uri("/api/v2/parse/upload")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(LlamaParseUploadResponse.class)
                .flatMap(uploadRes -> {
                    String fileId = uploadRes.getId();
                    log.info("PDF berhasil diunggah ke LlamaParse, fileId: {}. Memulai job parsing dengan tier: {}", fileId, tier);

                    Map<String, Object> parseRequest = Map.of(
                            "file_id", fileId,
                            "tier", tier,
                            "version", "latest"
                    );

                    return webClient.post()
                            .uri("/api/v2/parse")
                            .header("Authorization", "Bearer " + apiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(parseRequest)
                            .retrieve()
                            .bodyToMono(LlamaParseJobResponse.class);
                })
                .flatMap(jobRes -> {
                    log.info("Job parsing LlamaParse dibuat dengan jobId: {}. Memulai polling status...", jobRes.getId());
                    return pollJob(jobRes.getId());
                });
    }

    private Mono<String> pollJob(String jobId) {
        return webClient.get()
                .uri("/api/v2/parse/{jobId}?expand=markdown", jobId)
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(LlamaParseJobResponse.class)
                .flatMap(response -> {
                    String status = response.getStatus();
                    log.info("Status job LlamaParse {}: {}", jobId, status);

                    if ("COMPLETED".equals(status)) {
                        log.info("Job LlamaParse {} selesai. Mengekstrak teks markdown...", jobId);
                        if (response.getMarkdown() != null && response.getMarkdown().getPages() != null) {
                            String combinedMarkdown = response.getMarkdown().getPages().stream()
                                    .map(LlamaParsePage::getMarkdown)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.joining("\n\n"));
                            return Mono.just(combinedMarkdown);
                        }
                        return Mono.just("");
                    } else if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                        String errMsg = response.getErrorMessage() != null ? response.getErrorMessage() : "Unknown error";
                        return Mono.error(new RuntimeException("LlamaParse job " + jobId + " " + status.toLowerCase() + ". Error: " + errMsg));
                    } else {
                        // Job masih PENDING atau RUNNING, lakukan polling ulang dengan jeda 2 detik
                        return Mono.delay(Duration.ofSeconds(2))
                                .then(pollJob(jobId));
                    }
                });
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LlamaParseUploadResponse {
        private String id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LlamaParseJobResponse {
        private String id;
        private String status;
        private String errorMessage;
        private LlamaParseMarkdown markdown;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LlamaParseMarkdown {
        private List<LlamaParsePage> pages;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LlamaParsePage {
        private int page;
        private String markdown;
    }
}
