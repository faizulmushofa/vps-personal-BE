package io.github.faizul.ai.service;

import io.github.faizul.ai.dtos.AiRequest;
import io.github.faizul.ai.dtos.AiResponse;
import io.github.faizul.ai.model.AiWorkspaceChat;
import io.github.faizul.ai.model.AiWorkspaceMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AiWorkspaceChatService {
    Mono<AiWorkspaceChat> getOrCreateActiveChat(UUID workspaceId);
    Flux<AiWorkspaceChat> getChats(UUID workspaceId);
    Mono<AiResponse> chatWorkspace(UUID workspaceId, UUID chatId, AiRequest request, org.springframework.web.server.ServerWebExchange exchange);
    Flux<AiWorkspaceMessage> getChatMessages(UUID chatId);
    Mono<AiResponse> generateWorkspaceDoc(UUID workspaceId, String type, org.springframework.web.server.ServerWebExchange exchange);
}
