package io.github.faizul.ai.repository;

import io.github.faizul.ai.model.AiWorkspaceNote;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AiWorkspaceNoteRepository extends R2dbcRepository<AiWorkspaceNote, UUID> {
    Flux<AiWorkspaceNote> findAllByWorkspaceId(UUID workspaceId);
    Mono<AiWorkspaceNote> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
