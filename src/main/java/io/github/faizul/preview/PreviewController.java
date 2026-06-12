package io.github.faizul.preview;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST Controller untuk preview berkas secara inline di browser.
 * Semua endpoint mengembalikan Content-Disposition: inline agar
 * browser merender konten (gambar, video, PDF, audio) alih-alih mengunduhnya.
 */
@RestController
@RequestMapping("api/preview")
public class PreviewController {

    private final PreviewService previewService;

    public PreviewController(PreviewService previewService) {
        this.previewService = previewService;
    }

    /**
     * Preview file pribadi milik user yang terautentikasi.
     * Memerlukan JWT token di header Authorization.
     */
    @GetMapping("/{fileId}")
    public Mono<ResponseEntity<Flux<byte[]>>> previewPrivateFile(@PathVariable UUID fileId) {
        return previewService.previewPrivateFile(fileId)
                .map(result -> ResponseEntity.ok()
                        .header("Content-Disposition", "inline; filename=\"" + result.fileName() + "\"")
                        .header("Content-Length", String.valueOf(result.size()))
                        .contentType(MediaType.parseMediaType(result.contentType()))
                        .body(result.dataStream()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Preview file dari public share token (anonim, tanpa JWT).
     * Endpoint ini di-permit-all di SecurityConfig.
     *
     * @param provider "local" untuk StorageNode, "google" untuk Google Drive
     * @param shareToken Token unik dari tautan pembagian publik
     */
    @GetMapping("/public/{provider}/{shareToken}")
    public Mono<ResponseEntity<Flux<byte[]>>> previewPublicFile(
            @PathVariable String provider,
            @PathVariable String shareToken) {
        return previewService.previewPublicFile(shareToken, provider)
                .map(result -> ResponseEntity.ok()
                        .header("Content-Disposition", "inline; filename=\"" + result.fileName() + "\"")
                        .header("Content-Length", String.valueOf(result.size()))
                        .contentType(MediaType.parseMediaType(result.contentType()))
                        .body(result.dataStream()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
