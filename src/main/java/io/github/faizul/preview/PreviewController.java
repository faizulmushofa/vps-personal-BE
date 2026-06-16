package io.github.faizul.preview;

import io.github.faizul.activity.UserActivityService;
import io.github.faizul.security.filter.CurrentUserContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
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
    private final CurrentUserContext currentUserContext;
    private final UserActivityService userActivityService;

    public PreviewController(PreviewService previewService, CurrentUserContext currentUserContext, UserActivityService userActivityService) {
        this.previewService = previewService;
        this.currentUserContext = currentUserContext;
        this.userActivityService = userActivityService;
    }

    /**
     * Preview file pribadi milik user yang terautentikasi.
     * Memerlukan JWT token di header Authorization.
     */
    @GetMapping("/{fileId}")
    public Mono<ResponseEntity<Flux<byte[]>>> previewPrivateFile(
            @PathVariable String fileId,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Long externalAccountId,
            ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> previewService.previewPrivateFile(userId, fileId, provider, externalAccountId)
                        .flatMap(result -> userActivityService.log(userId, "PREVIEW_FILE", "Melihat pratinjau berkas pribadi ID: " + fileId, exchange)
                                .thenReturn(ResponseEntity.ok()
                                        .header("Content-Disposition", "inline; filename=\"" + result.fileName() + "\"")
                                        .header("Content-Length", String.valueOf(result.size()))
                                        .contentType(MediaType.parseMediaType(result.contentType()))
                                        .body(result.dataStream())))
                )
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
            @PathVariable String shareToken,
            @RequestParam(required = false) String fileId,
            ServerWebExchange exchange) {
        return previewService.previewPublicFile(shareToken, provider, fileId)
                .flatMap(result -> userActivityService.log(null, "PREVIEW_FILE_PUBLIC", "Melihat pratinjau berkas publik dengan share token: " + shareToken, exchange)
                        .thenReturn(ResponseEntity.ok()
                                .header("Content-Disposition", "inline; filename=\"" + result.fileName() + "\"")
                                .header("Content-Length", String.valueOf(result.size()))
                                .contentType(MediaType.parseMediaType(result.contentType()))
                                .body(result.dataStream())))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
