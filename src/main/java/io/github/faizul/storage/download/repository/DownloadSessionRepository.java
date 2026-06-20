package io.github.faizul.storage.download.repository;


import org.springframework.data.r2dbc.repository.R2dbcRepository;

import reactor.core.publisher.Mono;

import java.util.UUID;
import io.github.faizul.storage.download.model.DownloadSession;

public interface DownloadSessionRepository extends R2dbcRepository<DownloadSession, UUID> {
    Mono<DownloadSession> findFirstByFileIdOrderByCreatedAtDesc(UUID fileId);
}

