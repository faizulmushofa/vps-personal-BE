package io.github.faizul.storage.folder.local.service.impl;

import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.storage.file.dtos.FileResponse;
import io.github.faizul.storage.file.repository.FileRepository;
import io.github.faizul.storage.folder.dtos.*;
import io.github.faizul.storage.folder.dtos.FolderContentResponse;
import io.github.faizul.storage.folder.dtos.FolderCreateRequest;
import io.github.faizul.storage.folder.dtos.FolderMoveRequest;
import io.github.faizul.storage.folder.dtos.FolderResponse;
import io.github.faizul.storage.folder.model.Folder;
import io.github.faizul.storage.folder.repository.FolderRepository;
import io.github.faizul.storage.folder.local.service.StorageNodeFolderService;
import io.github.faizul.storage.upload.service.UploadStorageService;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;



@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class StorageNodeFolderServiceImpl implements StorageNodeFolderService {

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final CurrentUserContext currentUserContext;
    private final UserActivityService userActivityService;
    private final UploadStorageService uploadStorageClient;

    @Override
    public Mono<FolderResponse> createFolder(FolderCreateRequest request, ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> {
                    Folder folder = Folder.builder()
                            .id(UUID.randomUUID())
                            .name(request.name())
                            .parentId(request.parentId())
                            .userId(userId)
                            .build();

                    Mono<Void> validateParent = Mono.empty();
                    if (request.parentId() != null) {
                        validateParent = folderRepository.findByIdAndUserId(request.parentId(), userId)
                                .switchIfEmpty(Mono.error(new NoSuchElementException("Folder parent tidak ditemukan!")))
                                .then();
                    }

                    return validateParent
                            .then(folderRepository.save(folder))
                            .flatMap(saved -> userActivityService.log(
                                    userId,
                                    "CREATE_FOLDER",
                                    "Membuat folder baru: " + saved.getName(),
                                    exchange
                            ).thenReturn(new FolderResponse(
                                    saved.getId(),
                                    saved.getName(),
                                    saved.getParentId(),
                                    saved.getUserId(),
                                    saved.getCreatedAt()
                            )));
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Mono<FolderContentResponse> getFolderContents(UUID parentId) {
        return currentUserContext.getUserId()
                .flatMap(userId -> {
                    Mono<Void> checkAccess = Mono.empty();
                    if (parentId != null) {
                        checkAccess = folderRepository.findByIdAndUserId(parentId, userId)
                                .switchIfEmpty(Mono.error(new AccessDeniedException("Anda tidak memiliki akses ke folder ini.")))
                                .then();
                    }

                    return checkAccess.then(Mono.zip(
                            // 1. Get folders
                            (parentId == null 
                                    ? folderRepository.findByUserIdAndParentIdIsNull(userId) 
                                    : folderRepository.findByUserIdAndParentId(userId, parentId))
                                    .map(f -> new FolderResponse(f.getId(), f.getName(), f.getParentId(), f.getUserId(), f.getCreatedAt()))
                                    .collectList()
                                    .defaultIfEmpty(new ArrayList<>()),
                            // 2. Get local files in this folder (or root)
                            (parentId == null
                                    ? fileRepository.findByUserIdAndFolderIdIsNullAndProvider(userId, "STORAGE_NODE")
                                    : fileRepository.findByUserIdAndFolderIdAndProvider(userId, parentId, "STORAGE_NODE"))
                                    .map(file -> new FileResponse(
                                            file.getId(),
                                            file.getOriginalFileName(),
                                            file.getSize(),
                                            file.getCreatedAt(),
                                            file.getProvider(),
                                            file.getExternalAccountId(),
                                            null
                                    ))
                                    .collectList()
                                    .defaultIfEmpty(new ArrayList<>())
                    ).map(tuple -> new FolderContentResponse(tuple.getT1(), tuple.getT2())));
                });
    }

    @Override
    public Mono<Void> moveItem(FolderMoveRequest request, ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> {
                    // Validasi target folder jika tidak null
                    Mono<Void> validateTarget = Mono.empty();
                    if (request.targetFolderId() != null) {
                        validateTarget = folderRepository.findByIdAndUserId(request.targetFolderId(), userId)
                                .switchIfEmpty(Mono.error(new NoSuchElementException("Folder tujuan tidak ditemukan atau tidak dapat diakses.")))
                                .then();
                    }

                    if ("FILE".equalsIgnoreCase(request.type())) {
                        return validateTarget
                                .then(fileRepository.findById(request.sourceId())
                                        .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan!")))
                                        .flatMap(file -> {
                                            if (!file.getUserId().equals(userId)) {
                                                return Mono.error(new AccessDeniedException("Anda tidak memiliki akses untuk memindahkan berkas ini."));
                                            }
                                            
                                            // Validasi provider: pemindahan file lokal hanya boleh ke folder lokal (targetFolderId must exist/be validated)
                                            if (!"STORAGE_NODE".equalsIgnoreCase(file.getProvider())) {
                                                return Mono.error(new IllegalArgumentException("Pemindahan berkas secara langsung antar provider berbeda tidak diizinkan. Silakan gunakan fitur Migrasi untuk memindahkan berkas antara Google Drive dan Local VPS Storage."));
                                            }

                                            file.setFolderId(request.targetFolderId());
                                            return fileRepository.save(file)
                                                    .flatMap(saved -> userActivityService.log(
                                                            userId,
                                                            "MOVE_FILE",
                                                            "Memindahkan berkas: " + saved.getOriginalFileName() + " ke folder " + (request.targetFolderId() != null ? request.targetFolderId() : "Root"),
                                                            exchange
                                                    ));
                                        }))
                                .then();
                    } else if ("FOLDER".equalsIgnoreCase(request.type())) {
                        if (request.sourceId().equals(request.targetFolderId())) {
                            return Mono.error(new IllegalArgumentException("Tidak dapat memindahkan folder ke dalam dirinya sendiri."));
                        }

                        return validateTarget
                                .then(isDescendant(request.targetFolderId(), request.sourceId())
                                        .flatMap(isDescendant -> {
                                            if (isDescendant) {
                                                return Mono.error(new IllegalArgumentException("Tidak dapat memindahkan folder ke dalam sub-foldernya sendiri."));
                                            }

                                            return folderRepository.findByIdAndUserId(request.sourceId(), userId)
                                                    .switchIfEmpty(Mono.error(new NoSuchElementException("Folder tidak ditemukan!")))
                                                    .flatMap(folder -> {
                                                        folder.setParentId(request.targetFolderId());
                                                        return folderRepository.save(folder)
                                                                .flatMap(saved -> userActivityService.log(
                                                                        userId,
                                                                        "MOVE_FOLDER",
                                                                        "Memindahkan folder: " + saved.getName() + " ke folder " + (request.targetFolderId() != null ? request.targetFolderId() : "Root"),
                                                                        exchange
                                                                ));
                                                    });
                                        }))
                                .then();
                    } else {
                        return Mono.error(new IllegalArgumentException("Tipe item pemindahan tidak valid: " + request.type()));
                    }
                });
    }

    @Override
    public Mono<Void> deleteFolder(UUID id, ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> folderRepository.findByIdAndUserId(id, userId)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("Folder tidak ditemukan atau tidak dapat diakses.")))
                        .flatMap(folder -> deleteFolderRecursive(folder.getId(), userId)
                                .then(userActivityService.log(
                                        userId,
                                        "DELETE_FOLDER",
                                        "Menghapus folder beserta seluruh isinya secara permanen: " + folder.getName(),
                                        exchange
                                ))
                        )
                ).then();
    }

    private Mono<Boolean> isDescendant(UUID childId, UUID potentialAncestorId) {
        if (childId == null || potentialAncestorId == null) {
            return Mono.just(false);
        }
        if (childId.equals(potentialAncestorId)) {
            return Mono.just(true);
        }
        return folderRepository.findById(childId)
                .flatMap(folder -> {
                    if (folder.getParentId() == null) {
                        return Mono.just(false);
                    }
                    if (folder.getParentId().equals(potentialAncestorId)) {
                        return Mono.just(true);
                    }
                    return isDescendant(folder.getParentId(), potentialAncestorId);
                })
                .defaultIfEmpty(false);
    }

    private Mono<Void> deleteFolderRecursive(UUID folderId, Long userId) {
        // 1. Hapus file secara fisik di Storage Node & database
        Mono<Void> deleteFiles = fileRepository.findByFolderId(folderId)
                .flatMap(file -> uploadStorageClient.deleteFile(userId, file.getId().toString())
                        .onErrorResume(e -> {
                            log.warn("Warning: Gagal menghapus file fisik {} di Storage Node: {}", file.getId(), e.getMessage());
                            return Mono.empty();
                        })
                        .then(fileRepository.delete(file))
                )
                .then();

        // 2. Cari sub-folder dan hapus secara rekursif
        Mono<Void> deleteSubfolders = folderRepository.findByUserIdAndParentId(userId, folderId)
                .flatMap(subFolder -> deleteFolderRecursive(subFolder.getId(), userId))
                .then();

        // 3. Hapus folder ini sendiri
        return deleteFiles.then(deleteSubfolders)
                .then(folderRepository.deleteById(folderId));
    }
}
