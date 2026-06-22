package io.github.faizul.ai.service.impl;

import io.github.faizul.ai.model.AiWorkspace;
import io.github.faizul.ai.model.AiWorkspaceFile;
import io.github.faizul.ai.model.AiWorkspaceNote;
import io.github.faizul.ai.repository.AiWorkspaceFileRepository;
import io.github.faizul.ai.repository.AiWorkspaceNoteRepository;
import io.github.faizul.ai.repository.AiWorkspaceRepository;
import io.github.faizul.ai.service.AiService;
import io.github.faizul.ai.service.AiWorkspaceService;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.storage.file.dtos.FileResponse;
import io.github.faizul.storage.file.local.service.StorageNodeFileService;
import io.github.faizul.storage.file.repository.FileRepository;
import io.github.faizul.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AiWorkspaceServiceImpl implements AiWorkspaceService {

    private final AiWorkspaceRepository aiWorkspaceRepository;
    private final AiWorkspaceFileRepository aiWorkspaceFileRepository;
    private final AiWorkspaceNoteRepository aiWorkspaceNoteRepository;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final CurrentUserContext currentUserContext;
    private final AiService aiService;
    private final DatabaseClient databaseClient;
    private final StorageNodeFileService storageNodeFileService;

    @Override
    public Mono<AiWorkspace> createWorkspace(String name, String description) {
        return currentUserContext.getUserId()
                .flatMap(userId -> userRepository.findById(userId)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("User tidak ditemukan!")))
                        .flatMap(user -> aiWorkspaceRepository.countByUserId(userId)
                                .flatMap(count -> {
                                    int maxWorkspaces = user.getSubscriptionPlan().getLimits().maxWorkspaces();
                                    if (maxWorkspaces != -1 && count >= maxWorkspaces) {
                                        return Mono.error(new IllegalArgumentException(
                                                "Batas maksimal ruang kerja AI terhubung (" + maxWorkspaces + ") telah tercapai untuk paket Anda."));
                                    }
                                    AiWorkspace ws = AiWorkspace.builder()
                                            .id(UUID.randomUUID())
                                            .userId(userId)
                                            .name(name)
                                            .description(description)
                                            .isNewRecord(true)
                                            .build();
                                    return aiWorkspaceRepository.save(ws);
                                })
                        )
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<AiWorkspace> getWorkspaces() {
        return currentUserContext.getUserId()
                .flatMapMany(aiWorkspaceRepository::findAllByUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<AiWorkspace> getWorkspace(UUID workspaceId) {
        return currentUserContext.getUserId()
                .flatMap(userId -> aiWorkspaceRepository.findByIdAndUserId(workspaceId, userId)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("Ruang kerja AI tidak ditemukan!"))));
    }

    @Override
    public Mono<Void> deleteWorkspace(UUID workspaceId) {
        return getWorkspace(workspaceId)
                .flatMap(ws -> aiWorkspaceFileRepository.deleteByWorkspaceId(workspaceId)
                        .then(databaseClient.sql("DELETE FROM ai_workspace_notes WHERE workspace_id = :wsId").bind("wsId", workspaceId).then())
                        .then(databaseClient.sql("DELETE FROM ai_workspace_chats WHERE workspace_id = :wsId").bind("wsId", workspaceId).then())
                        .then(aiWorkspaceRepository.delete(ws)));
    }

    @Override
    public Mono<Void> addFileSource(UUID workspaceId, String fileId, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> storageNodeFileService.resolveFileId(fileId, userId))
                .flatMap(fileUuid -> getWorkspace(workspaceId)
                        .flatMap(ws -> fileRepository.findById(fileUuid)
                                .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan!")))
                                .flatMap(file -> {
                                    // Cek jika relasi file sudah ada
                                    return aiWorkspaceFileRepository.existsByWorkspaceIdAndFileId(workspaceId, fileUuid)
                                            .flatMap(exists -> {
                                                if (Boolean.TRUE.equals(exists)) {
                                                    return Mono.empty();
                                                }
                                                AiWorkspaceFile link = AiWorkspaceFile.builder()
                                                         .workspaceId(workspaceId)
                                                         .fileId(fileUuid)
                                                         .isNewRecord(true)
                                                         .build();
                                                return aiWorkspaceFileRepository.save(link);
                                            })
                                            .then(Mono.fromRunnable(() -> {
                                                // Pemicu parsing & summarization di background secara non-blocking
                                                log.info("Memicu JIT ekstraksi dan ringkasan AI di background untuk fileId: {}", fileUuid);
                                                aiService.summarizePdf(fileUuid, exchange)
                                                         .subscribeOn(Schedulers.boundedElastic())
                                                         .subscribe(
                                                                 summary -> log.info("Berhasil membuat JIT ringkasan untuk fileId: {}", fileUuid),
                                                                 err -> log.error("Gagal membuat JIT ringkasan untuk fileId: {}", fileUuid, err)
                                                         );
                                            }))
                                            .then();
                                })
                        )
                );
    }

    @Override
    public Mono<Void> removeFileSource(UUID workspaceId, UUID fileId) {
        return getWorkspace(workspaceId)
                .flatMap(ws -> aiWorkspaceFileRepository.deleteByWorkspaceIdAndFileId(workspaceId, fileId));
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<FileResponse> getWorkspaceFiles(UUID workspaceId) {
        return getWorkspace(workspaceId)
                .flatMapMany(ws -> aiWorkspaceFileRepository.findAllByWorkspaceId(workspaceId)
                        .flatMap(wsFile -> fileRepository.findById(wsFile.getFileId()))
                        .map(file -> new FileResponse(
                                file.getId(),
                                file.getOriginalFileName(),
                                file.getSize(),
                                file.getCreatedAt(),
                                file.getProvider(),
                                file.getExternalAccountId(),
                                null
                        )));
    }

    @Override
    public Mono<AiWorkspaceNote> createNote(UUID workspaceId, String title, String content) {
        return getWorkspace(workspaceId)
                .flatMap(ws -> {
                    AiWorkspaceNote note = AiWorkspaceNote.builder()
                            .id(UUID.randomUUID())
                            .workspaceId(workspaceId)
                            .title(title)
                            .content(content)
                            .isNewRecord(true)
                            .build();
                    return aiWorkspaceNoteRepository.save(note);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<AiWorkspaceNote> getNotes(UUID workspaceId) {
        return getWorkspace(workspaceId)
                .flatMapMany(ws -> aiWorkspaceNoteRepository.findAllByWorkspaceId(workspaceId));
    }

    @Override
    public Mono<AiWorkspaceNote> updateNote(UUID noteId, String title, String content) {
        return aiWorkspaceNoteRepository.findById(noteId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Catatan tidak ditemukan!")))
                .flatMap(note -> getWorkspace(note.getWorkspaceId()) // validasi kepemilikan workspace
                        .flatMap(ws -> {
                            note.setTitle(title);
                            note.setContent(content);
                            note.setNewRecord(false);
                            return aiWorkspaceNoteRepository.save(note);
                        }));
    }

    @Override
    public Mono<Void> deleteNote(UUID noteId) {
        return aiWorkspaceNoteRepository.findById(noteId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Catatan tidak ditemukan!")))
                .flatMap(note -> getWorkspace(note.getWorkspaceId()) // validasi kepemilikan workspace
                        .flatMap(ws -> aiWorkspaceNoteRepository.delete(note)));
    }
}
