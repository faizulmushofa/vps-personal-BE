package io.github.faizul.File.DownloadService;

import org.springframework.data.r2dbc.repository.R2dbcRepository;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DownloadSessionRepository extends R2dbcRepository<DownloadSession, UUID> {
    Mono<DownloadSession> findFirstByFileIdOrderByCreatedAtDesc(UUID fileId);
}

