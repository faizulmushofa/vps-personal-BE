package io.github.faizul.ai.repository;

import io.github.faizul.ai.model.AiWorkspaceFile;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AiWorkspaceFileRepository extends R2dbcRepository<AiWorkspaceFile, String> {
    Flux<AiWorkspaceFile> findAllByWorkspaceId(UUID workspaceId);
    Mono<Void> deleteByWorkspaceIdAndFileId(UUID workspaceId, UUID fileId);
    Mono<Void> deleteByWorkspaceId(UUID workspaceId);
    Mono<Boolean> existsByWorkspaceIdAndFileId(UUID workspaceId, UUID fileId);
}
