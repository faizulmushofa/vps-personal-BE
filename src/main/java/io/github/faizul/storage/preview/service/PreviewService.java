package io.github.faizul.storage.preview.service;

import reactor.core.publisher.Mono;

import java.util.UUID;
import io.github.faizul.storage.download.service.DownloadService;
import io.github.faizul.storage.file.local.service.StorageNodeFileService;
import io.github.faizul.storage.preview.model.PreviewResult;

/**
 * Service interface untuk preview berkas.
 * Tidak bergantung pada Repository layer manapun.
 */
public interface PreviewService {

    /**
     * Preview file pribadi milik user yang terautentikasi.
     * Akses dikontrol oleh DownloadService/StorageNodeFileService yang di-inject.
     */
    Mono<PreviewResult> previewPrivateFile(Long userId, String fileId, String provider, Long externalAccountId, org.springframework.web.server.ServerWebExchange exchange);

    /**
     * Preview file dari public share token (anonim, tanpa JWT).
     * @param shareToken Token unik dari tautan publik
     * @param provider "local" untuk StorageNode, "google" untuk Google Drive
     */
    Mono<PreviewResult> previewPublicFile(String shareToken, String provider, String fileId, org.springframework.web.server.ServerWebExchange exchange);
}
