package io.github.faizul.ai.repository;

import io.github.faizul.ai.model.AiWorkspace;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AiWorkspaceRepository extends R2dbcRepository<AiWorkspace, UUID> {
    Flux<AiWorkspace> findAllByUserId(Long userId);
    Mono<Long> countByUserId(Long userId);
    Mono<AiWorkspace> findByIdAndUserId(UUID id, Long userId);
}
