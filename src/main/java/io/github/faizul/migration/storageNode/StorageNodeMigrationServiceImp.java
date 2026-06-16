package io.github.faizul.migration.storageNode;

import io.github.faizul.File.core.File;
import io.github.faizul.File.core.FileRepository;
import io.github.faizul.File.core.googleDrive.GoogleDriveClient;
import io.github.faizul.Storage.upload.UploadStorageService;
import io.github.faizul.infra.config.StorageConfig;
import io.github.faizul.migration.*;
import io.github.faizul.migration.dtos.MigrationRequest;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.activity.UserActivityService;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@Transactional
public class StorageNodeMigrationServiceImp implements StorageNodeMigrationService {

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
    private final io.github.faizul.folder.core.FolderRepository folderRepository;

    public StorageNodeMigrationServiceImp(
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
            io.github.faizul.folder.core.FolderRepository folderRepository) {
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
                                    long totalBytesToMigrate = validFiles.stream().mapToLong(File::getSize).sum();
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
                                            .fileName(file.getOriginalFileName())
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
                                    processFolders = Flux.fromIterable(request.folderIds())
                                            .flatMap(gDriveFolderId -> googleDriveClient.getFileName(request.sourceExternalAccountId() != null ? request.sourceExternalAccountId() : 0L, gDriveFolderId)
                                                    .defaultIfEmpty("Google Drive Folder")
                                                    .flatMap(folderName -> {
                                                        UUID placeholderId = UUID.randomUUID();
                                                        MigrationTask folderTask = MigrationTask.builder()
                                                                .id(UUID.randomUUID())
                                                                .batchId(batchId)
                                                                .userId(userId)
                                                                .fileId(placeholderId)
                                                                .fileName("[Folder] " + folderName)
                                                                .sourceProvider("GOOGLE_DRIVE")
                                                                .targetProvider("STORAGE_NODE")
                                                                .targetExternalAccountId(request.targetExternalAccountId())
                                                                .deleteSource(request.deleteSource())
                                                                .status(MigrationStatus.PENDING)
                                                                .progress(0.0)
                                                                .updatedAt(Instant.now())
                                                                .build();
                                                        return migrationTaskRepository.save(folderTask)
                                                                .flatMap(savedTask -> {
                                                                    Flux.just(savedTask)
                                                                            .concatMap(task -> migrationService.updateTaskStatus(task.getId(), MigrationStatus.RUNNING, 0.0, null)
                                                                                    .then(migrateFolderFromGoogleDriveRecursive(userId, batchId, gDriveFolderId, null, request.sourceExternalAccountId(), request.deleteSource(), task.getId()))
                                                                                    .then(migrationService.updateTaskStatus(task.getId(), MigrationStatus.SUCCESS, 100.0, null))
                                                                                    .onErrorResume(err -> {
                                                                                        log.error("Folder GDrive migration failed for task: " + task.getId(), err);
                                                                                        return migrationService.updateTaskStatus(task.getId(), MigrationStatus.FAILED, 0.0, err.getMessage());
                                                                                    })
                                                                            )
                                                                            .then()
                                                                            .subscribeOn(migrationScheduler)
                                                                            .subscribe(
                                                                                    success -> log.info("Batch GDrive to Storage Node folder migration {} completed successfully", batchId),
                                                                                    error -> log.error("Batch GDrive to Storage Node folder migration " + batchId + " failed", error)
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
        Long externalAccountId = file.getExternalAccountId();
        String googleFileId = file.getStorageName();
        UUID fileId = file.getId();
        Long userId = file.getUserId();
        long fileSize = file.getSize();

        long chunkSize = 5 * 1024 * 1024L; // 5 MB chunk size
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);

        Path tempDir = storageConfig.tempDir(userId, fileId);

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

                                Flux<byte[]> dataRange = googleDriveClient.downloadFileRange(externalAccountId, googleFileId, start, end);
                                
                                return writeBytesToFile(dataRange, tempFile)
                                        .then(Mono.defer(() -> {
                                            return uploadStorageService.sendBatch(userId, fileId, i, i);
                                        }))
                                        .then(Mono.fromRunnable(() -> {
                                            try {
                                                Files.deleteIfExists(tempFile);
                                            } catch (IOException e) {
                                                log.warn("Failed to delete temp migration chunk {}", tempFile, e);
                                            }
                                        }))
                                        .then(Mono.defer(() -> {
                                            double progress = ((double) (i + 1) / totalChunks) * 100.0;
                                            return migrationService.updateTaskProgress(task.getId(), progress);
                                        }));
                            });
                })
                .then(Mono.defer(() -> {
                    return uploadStorageService.sendFinalSignal(userId, fileId, totalChunks);
                }))
                .then(Mono.defer(() -> {
                    if (Boolean.TRUE.equals(task.getDeleteSource())) {
                        return googleDriveClient.deleteFile(externalAccountId, googleFileId)
                                .onErrorResume(err -> {
                                    log.warn("Failed to delete file from GDrive after migration", err);
                                    return Mono.empty();
                                });
                    }
                    return Mono.empty();
                }))
                .then(Mono.defer(() -> {
                    UUID folderUuid = task.getTargetFolderId() != null ? UUID.fromString(task.getTargetFolderId()) : null;
                    if (Boolean.TRUE.equals(task.getDeleteSource())) {
                        file.setProvider("STORAGE_NODE");
                        file.setStorageName(fileId.toString());
                        file.setExternalAccountId(null);
                        file.setFolderId(folderUuid);
                        return fileRepository.save(file);
                    } else {
                        File copyFile = File.builder()
                                .id(UUID.randomUUID())
                                .userId(userId)
                                .originalFileName(file.getOriginalFileName())
                                .storageName(fileId.toString())
                                .size(fileSize)
                                .provider("STORAGE_NODE")
                                .folderId(folderUuid)
                                .build();
                        return fileRepository.save(copyFile);
                    }
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

    private Mono<Void> migrateFolderFromGoogleDriveRecursive(
            Long userId,
            UUID batchId,
            String gDriveFolderId,
            UUID parentLocalFolderId,
            Long externalAccountId,
            boolean deleteSource,
            UUID folderTaskId) {

        return googleDriveClient.getFileName(externalAccountId, gDriveFolderId)
                .flatMap(folderName -> {
                    io.github.faizul.folder.core.Folder newLocalFolder = io.github.faizul.folder.core.Folder.builder()
                            .id(UUID.randomUUID())
                            .name(folderName)
                            .parentId(parentLocalFolderId)
                            .userId(userId)
                            .build();

                    return folderRepository.save(newLocalFolder)
                            .flatMap(savedLocalFolder -> {
                                UUID newLocalFolderId = savedLocalFolder.getId();

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
                                                    MigrationTask subTask = MigrationTask.builder()
                                                            .id(subFolderTaskId)
                                                            .batchId(batchId)
                                                            .userId(userId)
                                                            .fileId(UUID.randomUUID())
                                                            .fileName("[Folder] " + itemName)
                                                            .sourceProvider("GOOGLE_DRIVE")
                                                            .targetProvider("STORAGE_NODE")
                                                            .targetExternalAccountId(externalAccountId)
                                                            .deleteSource(deleteSource)
                                                            .status(MigrationStatus.RUNNING)
                                                            .progress(0.0)
                                                            .updatedAt(Instant.now())
                                                            .build();
                                                    tasks.add(migrationTaskRepository.save(subTask)
                                                            .then(migrateFolderFromGoogleDriveRecursive(
                                                                    userId,
                                                                    batchId,
                                                                    itemId,
                                                                    newLocalFolderId,
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
                                                            .originalFileName(itemName)
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
                                                            .fileName(itemName)
                                                            .sourceProvider("GOOGLE_DRIVE")
                                                            .targetProvider("STORAGE_NODE")
                                                            .deleteSource(deleteSource)
                                                            .status(MigrationStatus.PENDING)
                                                            .progress(0.0)
                                                            .targetFolderId(newLocalFolderId.toString())
                                                            .updatedAt(Instant.now())
                                                            .build());
                                                }
                                            }

                                            Mono<Void> processFiles = Mono.empty();
                                            if (!migrationTasks.isEmpty()) {
                                                processFiles = fileRepository.saveAll(filesToMigrate)
                                                        .then(migrationTaskRepository.saveAll(migrationTasks).collectList())
                                                        .flatMap(savedTasks -> {
                                                            runMigrationTasksInBackground(savedTasks, filesToMigrate)
                                                                    .delaySubscription(java.time.Duration.ofMillis(500))
                                                                    .subscribeOn(migrationScheduler)
                                                                    .subscribe(
                                                                            success -> log.info("Folder files GDrive to local migration completed successfully for GDrive folder {}", gDriveFolderId),
                                                                            error -> log.error("Folder files GDrive to local migration failed for GDrive folder " + gDriveFolderId, error)
                                                                    );
                                                            return Mono.empty();
                                                        });
                                            }

                                            Mono<Void> processSubfolders = Flux.merge(tasks).then();

                                            Mono<Void> deleteSelf = Mono.empty();
                                            if (deleteSource) {
                                                deleteSelf = googleDriveClient.deleteFile(externalAccountId, gDriveFolderId).then();
                                            }

                                            return processFiles.then(processSubfolders).then(deleteSelf);
                                        });
                            });
                });
    }
}
