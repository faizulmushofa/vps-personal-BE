package io.github.faizul.ai.repository;

import io.github.faizul.ai.model.AiWorkspaceChat;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AiWorkspaceChatRepository extends R2dbcRepository<AiWorkspaceChat, UUID> {
    Mono<AiWorkspaceChat> findFirstByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
    Flux<AiWorkspaceChat> findAllByWorkspaceId(UUID workspaceId);
}
