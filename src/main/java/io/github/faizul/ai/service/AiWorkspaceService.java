package io.github.faizul.ai.service;

import io.github.faizul.ai.model.AiWorkspace;
import io.github.faizul.ai.model.AiWorkspaceNote;
import io.github.faizul.storage.file.dtos.FileResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AiWorkspaceService {
    Mono<AiWorkspace> createWorkspace(String name, String description);
    Flux<AiWorkspace> getWorkspaces();
    Mono<AiWorkspace> getWorkspace(UUID workspaceId);
    Mono<Void> deleteWorkspace(UUID workspaceId);

    Mono<Void> addFileSource(UUID workspaceId, String fileId, org.springframework.web.server.ServerWebExchange exchange);
    Mono<Void> removeFileSource(UUID workspaceId, UUID fileId);
    Flux<FileResponse> getWorkspaceFiles(UUID workspaceId);

    Mono<AiWorkspaceNote> createNote(UUID workspaceId, String title, String content);
    Flux<AiWorkspaceNote> getNotes(UUID workspaceId);
    Mono<AiWorkspaceNote> updateNote(UUID noteId, String title, String content);
    Mono<Void> deleteNote(UUID noteId);
}
