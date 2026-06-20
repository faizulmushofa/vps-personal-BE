package io.github.faizul.storage.migration.local.service.impl;

import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.infra.config.StorageConfig;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.storage.file.model.File;
import io.github.faizul.storage.file.repository.FileRepository;
import io.github.faizul.storage.file.service.client.GoogleDriveClient;
import io.github.faizul.storage.folder.model.Folder;
import io.github.faizul.storage.folder.repository.FolderRepository;
import io.github.faizul.storage.migration.dtos.MigrationRequest;
import io.github.faizul.storage.migration.model.MigrationStatus;
import io.github.faizul.storage.migration.model.MigrationTask;
import io.github.faizul.storage.migration.repository.MigrationTaskRepository;
import io.github.faizul.storage.migration.service.MigrationService;
import io.github.faizul.storage.migration.local.service.StorageNodeMigrationService;
import io.github.faizul.storage.upload.service.UploadStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Slf4j
@Service
@Transactional
public class StorageNodeMigrationServiceImpl implements StorageNodeMigrationService {

    private final MigrationTaskRepository migrationTaskRepository;
    private final FileRepository fileRepository;
    private final GoogleDriveClient googleDriveClient;
    private final UploadStorageService uploadStorageService;
    private final CurrentUserContext currentUserContext;
    private final StorageConfig storageConfig;
    private final DatabaseClient databaseClient;
    private final MigrationService migrationService;
    private final UserActivityService userActivityService;
    private final Scheduler migrationScheduler;
    private final FolderRepository folderRepository;

    public StorageNodeMigrationServiceImpl(
            MigrationTaskRepository migrationTaskRepository,
            FileRepository fileRepository,
            GoogleDriveClient googleDriveClient,
            UploadStorageService uploadStorageService,
            CurrentUserContext currentUserContext,
            StorageConfig storageConfig,
            DatabaseClient databaseClient,
            MigrationService migrationService,
            UserActivityService userActivityService,
            @Qualifier("migrationScheduler") Scheduler migrationScheduler,
            FolderRepository folderRepository) {
        this.migrationTaskRepository = migrationTaskRepository;
        this.fileRepository = fileRepository;
        this.googleDriveClient = googleDriveClient;
        this.uploadStorageService = uploadStorageService;
        this.currentUserContext = currentUserContext;
        this.storageConfig = storageConfig;
        this.databaseClient = databaseClient;
        this.migrationService = migrationService;
        this.userActivityService = userActivityService;
        this.migrationScheduler = migrationScheduler;
        this.folderRepository = folderRepository;
    }

    @Override
    public Mono<UUID> startStorageNodeMigration(MigrationRequest request) {
        if ((request.fileIds() == null || request.fileIds().isEmpty()) &&
            (request.folderIds() == null || request.folderIds().isEmpty())) {
            return Mono.error(new IllegalArgumentException("Daftar berkas/folder migrasi tidak boleh kosong."));
        }

        UUID batchId = UUID.randomUUID();

        return currentUserContext.getUserId()
                .flatMap(userId -> {
                    return migrationService.validateCommonLimits(userId)
                            .then(migrationService.getMigrationConfig())
                            .flatMap(configMap -> {
                                Long maxFileSizeBytes = (Long) configMap.get("maxFileSizeBytes");
                                List<String> fileIds = request.fileIds() != null ? request.fileIds() : List.of();
                                return migrationService.validateAndGetSourceFiles(
                                        fileIds,
                                        userId,
                                        maxFileSizeBytes,
                                        request.targetProvider(),
                                        request.targetExternalAccountId(),
                                        request.sourceExternalAccountId()
                                ).flatMap(validFiles -> {
                                    // Validate target quota limit on Storage Node
                                    long totalBytesToMigrate = validFiles.stream().mapToLong(f -> f.getSize() != null ? f.getSize() : 0L).sum();
                                    return fileRepository.calculateUsedStorageByUserId(userId)
                                            .flatMap(usedBytes -> {
                                                return databaseClient.sql("SELECT storage_quota FROM users WHERE id = :id")
                                                        .bind("id", userId)
                                                        .map((row, metadata) -> row.get("storage_quota", Long.class))
                                                        .one()
                                                        .defaultIfEmpty(1073741824L) // 1 GB fallback
                                                        .flatMap(quota -> {
                                                            if (usedBytes + totalBytesToMigrate > quota) {
                                                                 return Mono.error(new IllegalArgumentException("Kapasitas penyimpanan Storage Node tujuan tidak mencukupi untuk migrasi berkas terpilih."));
                                                            }
                                                            return Mono.just(validFiles);
                                                        });
                                            });
                                });
                            })
                            .flatMap(validFiles -> {
                                List<MigrationTask> tasks = new ArrayList<>();
                                for (File file : validFiles) {
                                    tasks.add(MigrationTask.builder()
                                            .id(UUID.randomUUID())
                                            .batchId(batchId)
                                            .userId(userId)
                                            .fileId(file.getId())
                                            .fileName(truncateFileName(file.getOriginalFileName()))
                                            .sourceProvider(file.getProvider())
                                            .targetProvider(request.targetProvider())
                                            .targetExternalAccountId(request.targetExternalAccountId())
                                            .deleteSource(request.deleteSource())
                                            .status(MigrationStatus.PENDING)
                                            .progress(0.0)
                                            .updatedAt(Instant.now())
                                            .build());
                                }

                                Mono<Void> processFiles = Mono.empty();
                                if (!tasks.isEmpty()) {
                                    processFiles = migrationTaskRepository.saveAll(tasks)
                                            .collectList()
                                            .flatMap(savedTasks -> {
                                                runMigrationTasksInBackground(savedTasks, validFiles)
                                                        .delaySubscription(java.time.Duration.ofMillis(500))
                                                        .subscribeOn(migrationScheduler)
                                                        .subscribe(
                                                                success -> log.info("Batch Storage Node migration {} completed successfully", batchId),
                                                                error -> log.error("Batch Storage Node migration " + batchId + " failed", error)
                                                        );
                                                return Mono.empty();
                                            });
                                }

                                Mono<Void> processFolders = Mono.empty();
                                if (request.folderIds() != null && !request.folderIds().isEmpty()) {
                                    // GDrive folder -> Local folder migration
                                    processFolders = Flux.fromIterable(request.folderIds())
                                            .flatMap(gDriveFolderId -> googleDriveClient.getFileName(request.sourceExternalAccountId(), gDriveFolderId)
                                                    .defaultIfEmpty("Google Drive Folder")
                                                    .flatMap(folderName -> {
                                                        UUID placeholderId = UUID.randomUUID();
                                                        MigrationTask folderTask = MigrationTask.builder()
                                                                .id(UUID.randomUUID())
                                                                .batchId(batchId)
                                                                .userId(userId)
                                                                .fileId(placeholderId)
                                                                .fileName(truncateFileName("[Folder] " + folderName))
                                                                .sourceProvider("GOOGLE_DRIVE")
                                                                .targetProvider("STORAGE_NODE")
                                                                .deleteSource(request.deleteSource())
                                                                .status(MigrationStatus.PENDING)
                                                                .progress(0.0)
                                                                .updatedAt(Instant.now())
                                                                .build();
                                                        return migrationTaskRepository.save(folderTask)
                                                                .flatMap(savedTask -> {
                                                                    Flux.just(savedTask)
                                                                            .concatMap(task -> migrationService.updateTaskStatus(task.getId(), MigrationStatus.RUNNING, 0.0, null)
                                                                                    .flatMap(updatedTask -> {
                                                                                        // Create a root folder on Storage Node for the migrated folder
                                                                                        Folder newLocalFolder = Folder.builder()
                                                                                                .id(UUID.randomUUID())
                                                                                                .userId(userId)
                                                                                                .name(folderName)
                                                                                                .parentId(null) // Root of the user storage
                                                                                                .build();
                                                                                        return folderRepository.save(newLocalFolder)
                                                                                                .flatMap(savedLocalFolder -> migrateFolderFromGoogleDriveToStorageNodeRecursive(
                                                                                                        userId,
                                                                                                        batchId,
                                                                                                        gDriveFolderId,
                                                                                                        savedLocalFolder.getId(),
                                                                                                        request.sourceExternalAccountId(),
                                                                                                        request.deleteSource(),
                                                                                                        task.getId()
                                                                                                ));
                                                                                    })
                                                                                    .then(migrationService.updateTaskStatus(task.getId(), MigrationStatus.SUCCESS, 100.0, null))
                                                                                    .onErrorResume(err -> {
                                                                                        log.error("Folder GDrive-to-Local migration failed for task: " + task.getId(), err);
                                                                                        return migrationService.updateTaskStatus(task.getId(), MigrationStatus.FAILED, 0.0, err.getMessage());
                                                                                    })
                                                                            )
                                                                            .then()
                                                                            .subscribeOn(migrationScheduler)
                                                                            .subscribe(
                                                                                    success -> log.info("Batch GDrive to Local folder migration {} completed successfully", batchId),
                                                                                    error -> log.error("Batch GDrive to Local folder migration " + batchId + " failed", error)
                                                                            );
                                                                    return Mono.empty();
                                                                });
                                                    })
                                            )
                                            .then();
                                }

                                return Mono.when(processFiles, processFolders).thenReturn(batchId);
                            });
                });
    }

    @Override
    public Mono<UUID> startStorageNodeMigrationWithLog(MigrationRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> userActivityService.log(
                        userId,
                        "MIGRATION_START",
                        "Memulai migrasi berkas massal ke VPS Storage Node (" + (request.fileIds() != null ? request.fileIds().size() : 0) + " berkas)",
                        exchange)
                        .then(startStorageNodeMigration(request))
                );
    }

    private Mono<Void> runMigrationTasksInBackground(List<MigrationTask> tasks, List<File> files) {
        Map<UUID, File> fileMap = new HashMap<>();
        for (File f : files) {
            fileMap.put(f.getId(), f);
        }

        return Flux.fromIterable(tasks)
                .concatMap(task -> {
                    File file = fileMap.get(task.getFileId());
                    return migrationTaskRepository.findById(task.getId())
                            .flatMap(currentTask -> {
                                if (currentTask.getStatus() == MigrationStatus.FAILED) {
                                    log.info("Task {} already cancelled in queue, skipping.", task.getId());
                                    return userActivityService.log(task.getUserId(), "MIGRATION_FAILED", 
                                            "Migrasi berkas dibatalkan: " + file.getOriginalFileName(), null)
                                            .then();
                                }
                                return runSingleFileMigration(task, file)
                                        .then(Mono.defer(() -> userActivityService.log(task.getUserId(), "MIGRATION_SUCCESS", 
                                                "Migrasi berkas berhasil: " + file.getOriginalFileName() + " dari " + file.getProvider() + " ke " + task.getTargetProvider(), null)))
                                        .onErrorResume(err -> {
                                            log.error("Error migrating file to Storage Node: " + file.getOriginalFileName(), err);
                                            try {
                                                org.springframework.util.FileSystemUtils.deleteRecursively(storageConfig.tempDir(task.getUserId(), task.getFileId()));
                                            } catch (IOException e) {
                                                log.warn("Failed to clean up temp dir on failure", e);
                                            }
                                            return migrationService.updateTaskStatus(task.getId(), MigrationStatus.FAILED, 0.0, err.getMessage())
                                                    .then(Mono.defer(() -> userActivityService.log(task.getUserId(), "MIGRATION_FAILED", 
                                                            "Migrasi berkas gagal: " + file.getOriginalFileName() + " (" + err.getMessage() + ")", null)));
                                        });
                            });
                })
                .then();
    }

    private Mono<Void> runSingleFileMigration(MigrationTask task, File file) {
        log.info("Starting Storage Node migration for task {} (File: {})", task.getId(), file.getOriginalFileName());

        return migrationService.updateTaskStatus(task.getId(), MigrationStatus.RUNNING, 0.0, null)
                .then(Mono.defer(() -> {
                    String src = file.getProvider();
                    if ("GOOGLE_DRIVE".equalsIgnoreCase(src)) {
                        return migrateGoogleToStorageNode(task, file);
                    } else {
                        return Mono.error(new IllegalArgumentException("Kombinasi perpindahan dari " + src + " ke Storage Node tidak didukung."));
                    }
                }));
    }

    private Mono<Void> migrateGoogleToStorageNode(MigrationTask task, File file) {
        Long srcAccountId = file.getExternalAccountId();
        String googleFileId = file.getStorageName();
        UUID sourceFileId = file.getId();
        Long userId = file.getUserId();
        long fileSize = file.getSize() != null ? file.getSize() : 0L;
        String fileName = file.getOriginalFileName();

        boolean deleteSource = Boolean.TRUE.equals(task.getDeleteSource());
        UUID targetFileId = deleteSource ? sourceFileId : UUID.randomUUID();

        long chunkSize = 5 * 1024 * 1024L; // 5 MB
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);

        Path tempDir = storageConfig.tempDir(userId, targetFileId);

        return Flux.range(0, totalChunks)
                .concatMap(i -> {
                    return migrationTaskRepository.findById(task.getId())
                            .flatMap(currentTask -> {
                                if (currentTask.getStatus() == MigrationStatus.FAILED) {
                                    return Mono.error(new IllegalStateException("Migrasi dibatalkan oleh pengguna."));
                                }
                                long start = i * chunkSize;
                                long end = Math.min(fileSize - 1, start + chunkSize - 1);
                                Path tempFile = tempDir.resolve("chunk-" + i);

                                Flux<byte[]> dataRange = googleDriveClient.downloadFileRange(srcAccountId, googleFileId, start, end);

                                return writeBytesToFile(dataRange, tempFile)
                                        .then(Mono.defer(() -> uploadStorageService.sendBatch(userId, targetFileId, i, i)))
                                        .then(Mono.fromCallable(() -> {
                                            try {
                                                Files.deleteIfExists(tempFile);
                                            } catch (IOException e) {
                                                log.warn("Failed to delete temp chunk {}", tempFile, e);
                                            }
                                            return true;
                                        }))
                                        .flatMap(ignored -> {
                                            double progress = ((double) (i + 1) / totalChunks) * 100.0;
                                            return migrationService.updateTaskProgress(task.getId(), Math.min(progress, 99.0));
                                        });
                            });
                })
                .last()
                .then(Mono.defer(() -> uploadStorageService.sendFinalSignal(userId, targetFileId, totalChunks)))
                .then(Mono.defer(() -> {
                    Mono<Void> deleteSourceMono = Mono.empty();
                    if (deleteSource) {
                        deleteSourceMono = googleDriveClient.deleteFile(srcAccountId, googleFileId)
                                .onErrorResume(err -> {
                                    log.warn("Failed to delete source file from GDrive", err);
                                    return Mono.empty();
                                });
                    }

                    return deleteSourceMono.then(Mono.defer(() -> {
                        if (deleteSource) {
                            file.setProvider("STORAGE_NODE");
                            file.setStorageName(targetFileId.toString());
                            file.setExternalAccountId(null);
                            file.setFolderId(task.getTargetFolderId() != null && !task.getTargetFolderId().isEmpty() ? UUID.fromString(task.getTargetFolderId()) : null);
                            return fileRepository.save(file);
                        } else {
                            File copyFile = File.builder()
                                     .id(targetFileId)
                                     .userId(userId)
                                     .originalFileName(fileName)
                                     .storageName(targetFileId.toString())
                                     .size(fileSize)
                                     .provider("STORAGE_NODE")
                                     .externalAccountId(null)
                                     .folderId(task.getTargetFolderId() != null && !task.getTargetFolderId().isEmpty() ? UUID.fromString(task.getTargetFolderId()) : null)
                                     .build();
                             return fileRepository.save(copyFile);
                        }
                    }));
                }))
                .then(migrationService.updateTaskStatus(task.getId(), MigrationStatus.SUCCESS, 100.0, null))
                .then();
    }

    private Mono<Void> writeBytesToFile(Flux<byte[]> bytesFlux, Path path) {
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            return Mono.error(e);
        }
        DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();
        Flux<DataBuffer> dataBufferFlux = bytesFlux.map(bufferFactory::wrap);
        return DataBufferUtils.write(dataBufferFlux, path).then();
    }

    private Mono<Void> migrateFolderFromGoogleDriveToStorageNodeRecursive(
            Long userId,
            UUID batchId,
            String gDriveFolderId,
            UUID parentLocalFolderId,
            Long externalAccountId,
            boolean deleteSource,
            UUID folderTaskId) {

        return googleDriveClient.listFilesAndFolders(externalAccountId, gDriveFolderId)
                .flatMap(list -> {
                    List<Mono<Void>> tasks = new ArrayList<>();
                    List<File> filesToMigrate = new ArrayList<>();
                    List<MigrationTask> migrationTasks = new ArrayList<>();

                    for (Map<String, Object> item : list) {
                        String itemId = (String) item.get("id");
                        String itemName = (String) item.get("name");
                        String mimeType = (String) item.get("mimeType");
                        Long size = item.get("size") != null ? Long.parseLong(item.get("size").toString()) : 0L;

                        if ("application/vnd.google-apps.folder".equals(mimeType)) {
                            UUID subFolderTaskId = UUID.randomUUID();
                            Folder newLocalSubfolder = Folder.builder()
                                    .id(UUID.randomUUID())
                                    .userId(userId)
                                    .name(itemName)
                                    .parentId(parentLocalFolderId)
                                    .build();

                            MigrationTask subTask = MigrationTask.builder()
                                    .id(subFolderTaskId)
                                    .batchId(batchId)
                                    .userId(userId)
                                    .fileId(newLocalSubfolder.getId())
                                    .fileName(truncateFileName("[Folder] " + itemName))
                                    .sourceProvider("GOOGLE_DRIVE")
                                    .targetProvider("STORAGE_NODE")
                                    .deleteSource(deleteSource)
                                    .status(MigrationStatus.RUNNING)
                                    .progress(0.0)
                                    .updatedAt(Instant.now())
                                    .build();

                            tasks.add(folderRepository.save(newLocalSubfolder)
                                    .then(migrationTaskRepository.save(subTask))
                                    .then(migrateFolderFromGoogleDriveToStorageNodeRecursive(
                                            userId,
                                            batchId,
                                            itemId,
                                            newLocalSubfolder.getId(),
                                            externalAccountId,
                                            deleteSource,
                                            subFolderTaskId
                                    ))
                                    .then(migrationService.updateTaskStatus(subFolderTaskId, MigrationStatus.SUCCESS, 100.0, null))
                                    .onErrorResume(err -> migrationService.updateTaskStatus(subFolderTaskId, MigrationStatus.FAILED, 0.0, err.getMessage()))
                            );
                        } else {
                            UUID fileId = UUID.randomUUID();
                            File file = File.builder()
                                    .id(fileId)
                                    .userId(userId)
                                    .originalFileName(truncateFileName(itemName))
                                    .storageName(itemId)
                                    .size(size)
                                    .provider("GOOGLE_DRIVE")
                                    .externalAccountId(externalAccountId)
                                    .build();

                            filesToMigrate.add(file);

                            migrationTasks.add(MigrationTask.builder()
                                    .id(UUID.randomUUID())
                                    .batchId(batchId)
                                    .userId(userId)
                                    .fileId(fileId)
                                    .fileName(truncateFileName(itemName))
                                    .sourceProvider("GOOGLE_DRIVE")
                                    .targetProvider("STORAGE_NODE")
                                    .deleteSource(deleteSource)
                                    .status(MigrationStatus.PENDING)
                                    .progress(0.0)
                                    .targetFolderId(parentLocalFolderId.toString())
                                    .updatedAt(Instant.now())
                                    .build());
                        }
                    }

                    Mono<Void> processFiles = Mono.empty();
                    if (!migrationTasks.isEmpty()) {
                        processFiles = fileRepository.saveAll(filesToMigrate)
                                .then(migrationTaskRepository.saveAll(migrationTasks).collectList())
                                .flatMap(savedTasks -> runMigrationTasksInBackground(savedTasks, filesToMigrate));
                    }

                    Mono<Void> processSubfolders = Flux.merge(tasks).then();

                    Mono<Void> deleteSelf = Mono.empty();
                    if (deleteSource) {
                        deleteSelf = googleDriveClient.deleteFile(externalAccountId, gDriveFolderId).then();
                    }

                    return processFiles.then(processSubfolders).then(deleteSelf);
                });
    }

    private String truncateFileName(String name) {
        if (name == null) return "";
        return name.substring(0, Math.min(name.length(), 255));
    }
}
