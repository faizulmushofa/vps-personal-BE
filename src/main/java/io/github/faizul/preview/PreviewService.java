package io.github.faizul.preview;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service interface untuk preview berkas.
 * Tidak bergantung pada Repository layer manapun.
 */
public interface PreviewService {

    /**
     * Preview file pribadi milik user yang terautentikasi.
     * Akses dikontrol oleh DownloadService/FileService yang di-inject.
     */
    Mono<PreviewResult> previewPrivateFile(UUID fileId);

    /**
     * Preview file dari public share token (anonim, tanpa JWT).
     * @param shareToken Token unik dari tautan publik
     * @param provider "local" untuk StorageNode, "google" untuk Google Drive
     */
    Mono<PreviewResult> previewPublicFile(String shareToken, String provider);
}
