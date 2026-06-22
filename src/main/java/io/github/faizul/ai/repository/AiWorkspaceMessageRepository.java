package io.github.faizul.ai.repository;

import io.github.faizul.ai.model.AiWorkspaceMessage;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AiWorkspaceMessageRepository extends R2dbcRepository<AiWorkspaceMessage, UUID> {
    Flux<AiWorkspaceMessage> findAllByChatIdOrderByCreatedAtAsc(UUID chatId);
    Mono<Void> deleteByChatId(UUID chatId);
}
