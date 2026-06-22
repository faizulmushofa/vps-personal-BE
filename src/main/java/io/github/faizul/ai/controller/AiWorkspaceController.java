package io.github.faizul.ai.controller;

import io.github.faizul.ai.dtos.*;
import io.github.faizul.ai.model.AiWorkspace;
import io.github.faizul.ai.model.AiWorkspaceChat;
import io.github.faizul.ai.model.AiWorkspaceMessage;
import io.github.faizul.ai.model.AiWorkspaceNote;
import io.github.faizul.ai.service.AiWorkspaceChatService;
import io.github.faizul.ai.service.AiWorkspaceService;
import io.github.faizul.storage.file.dtos.FileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/ai/workspaces")
public class AiWorkspaceController {

    private final AiWorkspaceService aiWorkspaceService;
    private final AiWorkspaceChatService aiWorkspaceChatService;

    @PostMapping
    public Mono<ResponseEntity<AiWorkspace>> createWorkspace(@RequestBody WorkspaceRequest req) {
        return aiWorkspaceService.createWorkspace(req.name(), req.description())
                .map(ws -> ResponseEntity.ok().body(ws));
    }

    @GetMapping
    public Mono<ResponseEntity<Flux<AiWorkspace>>> getWorkspaces() {
        return Mono.just(ResponseEntity.ok().body(aiWorkspaceService.getWorkspaces()));
    }

    @GetMapping("/{workspaceId}")
    public Mono<ResponseEntity<AiWorkspace>> getWorkspace(@PathVariable UUID workspaceId) {
        return aiWorkspaceService.getWorkspace(workspaceId)
                .map(ws -> ResponseEntity.ok().body(ws));
    }

    @DeleteMapping("/{workspaceId}")
    public Mono<ResponseEntity<Void>> deleteWorkspace(@PathVariable UUID workspaceId) {
        return aiWorkspaceService.deleteWorkspace(workspaceId)
                .thenReturn(ResponseEntity.ok().build());
    }

    @PostMapping("/{workspaceId}/files")
    public Mono<ResponseEntity<Void>> addFileSource(
            @PathVariable UUID workspaceId,
            @RequestBody AddFileRequest req,
            ServerWebExchange exchange) {
        return aiWorkspaceService.addFileSource(workspaceId, req.fileId(), exchange)
                .thenReturn(ResponseEntity.ok().build());
    }

    @DeleteMapping("/{workspaceId}/files/{fileId}")
    public Mono<ResponseEntity<Void>> removeFileSource(
            @PathVariable UUID workspaceId,
            @PathVariable UUID fileId) {
        return aiWorkspaceService.removeFileSource(workspaceId, fileId)
                .thenReturn(ResponseEntity.ok().build());
    }

    @GetMapping("/{workspaceId}/files")
    public Mono<ResponseEntity<Flux<FileResponse>>> getWorkspaceFiles(@PathVariable UUID workspaceId) {
        return Mono.just(ResponseEntity.ok().body(aiWorkspaceService.getWorkspaceFiles(workspaceId)));
    }

    @PostMapping("/{workspaceId}/notes")
    public Mono<ResponseEntity<AiWorkspaceNote>> createNote(
            @PathVariable UUID workspaceId,
            @RequestBody NoteRequest req) {
        return aiWorkspaceService.createNote(workspaceId, req.title(), req.content())
                .map(note -> ResponseEntity.ok().body(note));
    }

    @GetMapping("/{workspaceId}/notes")
    public Mono<ResponseEntity<Flux<AiWorkspaceNote>>> getNotes(@PathVariable UUID workspaceId) {
        return Mono.just(ResponseEntity.ok().body(aiWorkspaceService.getNotes(workspaceId)));
    }

    @PutMapping("/notes/{noteId}")
    public Mono<ResponseEntity<AiWorkspaceNote>> updateNote(
            @PathVariable UUID noteId,
            @RequestBody NoteRequest req) {
        return aiWorkspaceService.updateNote(noteId, req.title(), req.content())
                .map(note -> ResponseEntity.ok().body(note));
    }

    @DeleteMapping("/notes/{noteId}")
    public Mono<ResponseEntity<Void>> deleteNote(@PathVariable UUID noteId) {
        return aiWorkspaceService.deleteNote(noteId)
                .thenReturn(ResponseEntity.ok().build());
    }

    @PostMapping("/{workspaceId}/chats/active")
    public Mono<ResponseEntity<AiWorkspaceChat>> getOrCreateActiveChat(@PathVariable UUID workspaceId) {
        return aiWorkspaceChatService.getOrCreateActiveChat(workspaceId)
                .map(chat -> ResponseEntity.ok().body(chat));
    }

    @GetMapping("/{workspaceId}/chats/active")
    public Mono<ResponseEntity<AiWorkspaceChat>> getOrCreateActiveChatGet(@PathVariable UUID workspaceId) {
        return aiWorkspaceChatService.getOrCreateActiveChat(workspaceId)
                .map(chat -> ResponseEntity.ok().body(chat));
    }

    @GetMapping("/{workspaceId}/chats")
    public Mono<ResponseEntity<Flux<AiWorkspaceChat>>> getChats(@PathVariable UUID workspaceId) {
        return Mono.just(ResponseEntity.ok().body(aiWorkspaceChatService.getChats(workspaceId)));
    }

    @PostMapping("/{workspaceId}/chats/{chatId}")
    public Mono<ResponseEntity<AiResponse>> chatWorkspace(
            @PathVariable UUID workspaceId,
            @PathVariable UUID chatId,
            @RequestBody AiRequest request,
            ServerWebExchange exchange) {
        return aiWorkspaceChatService.chatWorkspace(workspaceId, chatId, request, exchange)
                .map(response -> ResponseEntity.ok().body(response));
    }

    @GetMapping("/chats/{chatId}/messages")
    public Mono<ResponseEntity<Flux<AiWorkspaceMessage>>> getChatMessages(@PathVariable UUID chatId) {
        return Mono.just(ResponseEntity.ok().body(aiWorkspaceChatService.getChatMessages(chatId)));
    }

    @PostMapping("/{workspaceId}/generate")
    public Mono<ResponseEntity<AiResponse>> generateWorkspaceDoc(
            @PathVariable UUID workspaceId,
            @RequestParam String type,
            ServerWebExchange exchange) {
        return aiWorkspaceChatService.generateWorkspaceDoc(workspaceId, type, exchange)
                .map(response -> ResponseEntity.ok().body(response));
    }
}
