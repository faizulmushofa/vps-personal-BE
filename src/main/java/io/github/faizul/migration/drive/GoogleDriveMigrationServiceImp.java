package io.github.faizul.migration.drive;

import io.github.faizul.File.core.File;
import io.github.faizul.File.core.FileRepository;
import io.github.faizul.File.core.googleDrive.GoogleDriveClient;
import io.github.faizul.Storage.download.DownloadStorageService;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class GoogleDriveMigrationServiceImp implements GoogleDriveMigrationService {

    private final MigrationTaskRepository migrationTaskRepository;
    private final FileRepository fileRepository;
    private final GoogleDriveClient googleDriveClient;
    private final UploadStorageService uploadStorageService;
    private final DownloadStorageService downloadStorageService;
    private final CurrentUserContext currentUserContext;
    private final StorageConfig storageConfig;
    private final MigrationService migrationService;
    private final UserActivityService userActivityService;
    private final Scheduler migrationScheduler;
    private final io.github.faizul.folder.core.FolderRepository folderRepository;

    public GoogleDriveMigrationServiceImp(
            MigrationTaskRepository migrationTaskRepository,
            FileRepository fileRepository,
            GoogleDriveClient googleDriveClient,
            UploadStorageService uploadStorageService,
            DownloadStorageService downloadStorageService,
            CurrentUserContext currentUserContext,
            StorageConfig storageConfig,
            MigrationService migrationService,
            UserActivityService userActivityService,
            @Qualifier("migrationScheduler") Scheduler migrationScheduler,
            io.github.faizul.folder.core.FolderRepository folderRepository) {
        this.migrationTaskRepository = migrationTaskRepository;
        this.fileRepository = fileRepository;
        this.googleDriveClient = googleDriveClient;
        this.uploadStorageService = uploadStorageService;
        this.downloadStorageService = downloadStorageService;
        this.currentUserContext = currentUserContext;
        this.storageConfig = storageConfig;
        this.migrationService = migrationService;
        this.userActivityService = userActivityService;
        this.migrationScheduler = migrationScheduler;
        this.folderRepository = folderRepository;
    }

    @Override
    public Mono<UUID> startGoogleDriveMigration(MigrationRequest request) {
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
                                                                    success -> log.info("Batch Google Drive file migration {} completed successfully", batchId),
                                                                    error -> log.error("Batch Google Drive file migration " + batchId + " failed", error)
                                                            );
                                                    return Mono.empty();
                                                });
                                    }

                                    Mono<Void> processFolders = Mono.empty();
                                    if (request.folderIds() != null && !request.folderIds().isEmpty()) {
                                        if (request.sourceExternalAccountId() != null) {
                                            // GDrive -> GDrive folder migration
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
                                                                        .targetProvider("GOOGLE_DRIVE")
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
                                                                                            .then(migrateFolderFromGoogleDriveToGoogleDriveRecursive(
                                                                                                    userId,
                                                                                                    batchId,
                                                                                                    gDriveFolderId,
                                                                                                    "root",
                                                                                                    request.sourceExternalAccountId(),
                                                                                                    request.targetExternalAccountId(),
                                                                                                    request.deleteSource(),
                                                                                                    task.getId()
                                                                                            ))
                                                                                            .then(migrationService.updateTaskStatus(task.getId(), MigrationStatus.SUCCESS, 100.0, null))
                                                                                            .onErrorResume(err -> {
                                                                                                log.error("Folder GDrive-to-GDrive migration failed for task: " + task.getId(), err);
                                                                                                return migrationService.updateTaskStatus(task.getId(), MigrationStatus.FAILED, 0.0, err.getMessage());
                                                                                            })
                                                                                    )
                                                                                    .then()
                                                                                    .subscribeOn(migrationScheduler)
                                                                                    .subscribe(
                                                                                            success -> log.info("Batch GDrive to GDrive folder migration {} completed successfully", batchId),
                                                                                            error -> log.error("Batch GDrive to GDrive folder migration " + batchId + " failed", error)
                                                                                    );
                                                                            return Mono.empty();
                                                                        });
                                                            })
                                                    )
                                                    .then();
                                        } else {
                                            // STORAGE_NODE -> GDrive folder migration
                                            List<UUID> folderUuids = request.folderIds().stream().map(UUID::fromString).collect(Collectors.toList());
                                            processFolders = folderRepository.findAllById(folderUuids)
                                                    .collectList()
                                                    .flatMap(folders -> {
                                                        List<MigrationTask> folderTasks = new ArrayList<>();
                                                        for (io.github.faizul.folder.core.Folder folder : folders) {
                                                            folderTasks.add(MigrationTask.builder()
                                                                    .id(UUID.randomUUID())
                                                                    .batchId(batchId)
                                                                    .userId(userId)
                                                                    .fileId(folder.getId())
                                                                    .fileName(truncateFileName("[Folder] " + folder.getName()))
                                                                    .sourceProvider("STORAGE_NODE")
                                                                    .targetProvider(request.targetProvider())
                                                                    .targetExternalAccountId(request.targetExternalAccountId())
                                                                    .deleteSource(request.deleteSource())
                                                                    .status(MigrationStatus.PENDING)
                                                                    .progress(0.0)
                                                                    .updatedAt(Instant.now())
                                                                    .build());
                                                        }
                                                        return migrationTaskRepository.saveAll(folderTasks)
                                                                .collectList()
                                                                .flatMap(savedFolderTasks -> {
                                                                    Flux.fromIterable(savedFolderTasks)
                                                                            .concatMap(task -> migrationService.updateTaskStatus(task.getId(), MigrationStatus.RUNNING, 0.0, null)
                                                                                    .then(migrateFolderRecursive(userId, batchId, task.getFileId(), "root", request.targetExternalAccountId(), request.deleteSource(), task.getId()))
                                                                                    .then(migrationService.updateTaskStatus(task.getId(), MigrationStatus.SUCCESS, 100.0, null))
                                                                                    .onErrorResume(err -> {
                                                                                        log.error("Folder migration failed for task: " + task.getId(), err);
                                                                                        return migrationService.updateTaskStatus(task.getId(), MigrationStatus.FAILED, 0.0, err.getMessage());
                                                                                    })
                                                                            )
                                                                            .then()
                                                                            .subscribeOn(migrationScheduler)
                                                                            .subscribe(
                                                                                    success -> log.info("Batch Google Drive folder migration {} completed successfully", batchId),
                                                                                    error -> log.error("Batch Google Drive folder migration " + batchId + " failed", error)
                                                                            );
                                                                    return Mono.empty();
                                                                });
                                                    });
                                        }
                                    }

                                    return Mono.when(processFiles, processFolders).thenReturn(batchId);
                                });
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
                                            log.error("Error migrating file to Google Drive: " + file.getOriginalFileName(), err);
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
        log.info("Starting GDrive migration for task {} (File: {})", task.getId(), file.getOriginalFileName());

        return migrationService.updateTaskStatus(task.getId(), MigrationStatus.RUNNING, 0.0, null)
                .then(Mono.defer(() -> {
                    String src = file.getProvider();
                    if ("STORAGE_NODE".equalsIgnoreCase(src)) {
                        return migrateStorageNodeToGoogle(task, file);
                    } else if ("GOOGLE_DRIVE".equalsIgnoreCase(src)) {
                        return migrateGoogleToGoogle(task, file);
                    } else {
                        return Mono.error(new IllegalArgumentException("Kombinasi perpindahan dari " + src + " ke Google Drive tidak didukung."));
                    }
                }));
    }

    private Mono<Void> migrateStorageNodeToGoogle(MigrationTask task, File file) {
        UUID fileId = file.getId();
        Long userId = file.getUserId();
        long fileSize = file.getSize() != null ? file.getSize() : 0L;
        String fileName = file.getOriginalFileName();
        Long targetAccountId = task.getTargetExternalAccountId();

        Path tempDir = storageConfig.tempDir(userId, fileId);

        String targetFolder = task.getTargetFolderId();
        return googleDriveClient.initiateResumableUpload(
                targetAccountId,
                fileName,
                detectMimeType(fileName),
                fileSize,
                targetFolder
        ).flatMap(uploadUrl -> {
            return downloadStorageService.downloadFile(userId, fileId)
                    .index()
                    .concatMap(tuple -> {
                        return migrationTaskRepository.findById(task.getId())
                                .flatMap(currentTask -> {
                                    if (currentTask.getStatus() == MigrationStatus.FAILED) {
                                        return Mono.error(new IllegalStateException("Migrasi dibatalkan oleh pengguna."));
                                    }
                                    int index = tuple.getT1().intValue();
                                    byte[] data = tuple.getT2().data();
                                    
                                    long start = index * 1024 * 1024L; // gRPC chunks are 1MB
                                    long end = start + data.length - 1;
                                    
                                    Path tempFile = tempDir.resolve("chunk-grpc-" + index);

                                    return writeBytesToFile(Flux.just(data), tempFile)
                                            .then(Mono.defer(() -> googleDriveClient.uploadChunkResumable(targetAccountId, uploadUrl, tempFile, start, end, fileSize)))
                                            .flatMap(googleFileId -> {
                                                try {
                                                    Files.deleteIfExists(tempFile);
                                                } catch (IOException e) {
                                                    log.warn("Failed to delete temp chunk {}", tempFile, e);
                                                }
                                                double progress = ((double) (end + 1) / fileSize) * 100.0;
                                                return migrationService.updateTaskProgress(task.getId(), Math.min(progress, 99.0))
                                                        .thenReturn(googleFileId);
                                            });
                                });
                    })
                    .last()
                    .flatMap(googleFileObj -> {
                        String googleFileId = (String) googleFileObj;
                        if (googleFileId == null || googleFileId.isEmpty()) {
                            return Mono.error(new IllegalStateException("Failed to retrieve uploaded Google File ID."));
                        }

                        Mono<Void> cleanupSource = Mono.empty();
                        if (Boolean.TRUE.equals(task.getDeleteSource())) {
                            cleanupSource = uploadStorageService.deleteFile(userId, fileId.toString())
                                    .onErrorResume(err -> {
                                        log.warn("Failed to delete physical file from Storage Node", err);
                                        return Mono.empty();
                                    });
                        }

                        return cleanupSource.then(Mono.defer(() -> {
                            if (Boolean.TRUE.equals(task.getDeleteSource())) {
                                file.setProvider("GOOGLE_DRIVE");
                                file.setStorageName(googleFileId);
                                file.setExternalAccountId(targetAccountId);
                                file.setFolderId(null);
                                return fileRepository.save(file);
                            } else {
                                 File copyFile = File.builder()
                                         .id(UUID.randomUUID())
                                         .userId(userId)
                                         .originalFileName(fileName)
                                         .storageName(googleFileId)
                                         .size(fileSize)
                                         .provider("GOOGLE_DRIVE")
                                         .externalAccountId(targetAccountId)
                                         .build();
                                return fileRepository.save(copyFile);
                            }
                        }));
                    })
                    .then(migrationService.updateTaskStatus(task.getId(), MigrationStatus.SUCCESS, 100.0, null));
        }).then();
    }

    private Mono<Void> migrateGoogleToGoogle(MigrationTask task, File file) {
        Long srcAccountId = file.getExternalAccountId();
        String googleFileId = file.getStorageName();
        UUID fileId = file.getId();
        Long userId = file.getUserId();
        long fileSize = file.getSize() != null ? file.getSize() : 0L;
        String fileName = file.getOriginalFileName();
        Long destAccountId = task.getTargetExternalAccountId();

        long chunkSize = 5 * 1024 * 1024L; // 5 MB
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);

        Path tempDir = storageConfig.tempDir(userId, fileId);

        String targetFolder = task.getTargetFolderId();
        return googleDriveClient.initiateResumableUpload(
                destAccountId,
                fileName,
                detectMimeType(fileName),
                fileSize,
                targetFolder
        ).flatMap(uploadUrl -> {
            return Flux.range(0, totalChunks)
                    .concatMap(i -> {
                        return migrationTaskRepository.findById(task.getId())
                                .flatMap(currentTask -> {
                                    if (currentTask.getStatus() == MigrationStatus.FAILED) {
                                        return Mono.error(new IllegalStateException("Migrasi dibatalkan oleh pengguna."));
                                    }
                                    long start = i * chunkSize;
                                    long end = Math.min(fileSize - 1, start + chunkSize - 1);
                                    Path tempFile = tempDir.resolve("chunk-gdgd-" + i);

                                    Flux<byte[]> dataRange = googleDriveClient.downloadFileRange(srcAccountId, googleFileId, start, end);

                                    return writeBytesToFile(dataRange, tempFile)
                                            .then(Mono.defer(() -> googleDriveClient.uploadChunkResumable(destAccountId, uploadUrl, tempFile, start, end, fileSize)))
                                            .flatMap(newId -> {
                                                try {
                                                    Files.deleteIfExists(tempFile);
                                                } catch (IOException e) {
                                                    log.warn("Failed to delete temp chunk {}", tempFile, e);
                                                }
                                                double progress = ((double) (i + 1) / totalChunks) * 100.0;
                                                return migrationService.updateTaskProgress(task.getId(), Math.min(progress, 99.0))
                                                        .thenReturn(newId);
                                            });
                                });
                    })
                    .last()
                    .flatMap(newGoogleFileObj -> {
                        String newGoogleFileId = (String) newGoogleFileObj;
                        if (newGoogleFileId == null || newGoogleFileId.isEmpty()) {
                            return Mono.error(new IllegalStateException("Failed to retrieve uploaded Google File ID."));
                        }

                        Mono<Void> deleteSource = Mono.empty();
                        if (Boolean.TRUE.equals(task.getDeleteSource())) {
                            deleteSource = googleDriveClient.deleteFile(srcAccountId, googleFileId)
                                    .onErrorResume(err -> {
                                        log.warn("Failed to delete source file from GDrive A", err);
                                        return Mono.empty();
                                    });
                        }

                        return deleteSource.then(Mono.defer(() -> {
                            if (Boolean.TRUE.equals(task.getDeleteSource())) {
                                file.setStorageName(newGoogleFileId);
                                file.setExternalAccountId(destAccountId);
                                return fileRepository.save(file);
                            } else {
                                File copyFile = File.builder()
                                         .id(UUID.randomUUID())
                                         .userId(userId)
                                         .originalFileName(fileName)
                                         .storageName(newGoogleFileId)
                                         .size(fileSize)
                                         .provider("GOOGLE_DRIVE")
                                         .externalAccountId(destAccountId)
                                         .build();
                                return fileRepository.save(copyFile);
                            }
                        }));
                    })
                    .then(migrationService.updateTaskStatus(task.getId(), MigrationStatus.SUCCESS, 100.0, null));
        }).then();
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

    private String detectMimeType(String filename) {
        return org.springframework.http.MediaTypeFactory.getMediaType(filename)
                .map(org.springframework.http.MediaType::toString)
                .orElse("application/octet-stream");
    }

    private Mono<Void> migrateFolderRecursive(Long userId, UUID batchId, UUID localFolderId, String parentGoogleFolderId, Long targetAccountId, boolean deleteSource, UUID folderTaskId) {
        return folderRepository.findByIdAndUserId(localFolderId, userId)
                .flatMap(folder -> {
                    return googleDriveClient.createFolder(targetAccountId, folder.getName(), parentGoogleFolderId)
                            .flatMap(newGoogleFolderId -> {
                                Mono<Void> migrateFiles = fileRepository.findByUserIdAndFolderIdAndProvider(userId, localFolderId, "STORAGE_NODE")
                                        .collectList()
                                        .flatMap(files -> {
                                            if (files.isEmpty()) return Mono.empty();
                                            List<MigrationTask> tasks = new ArrayList<>();
                                            for (File file : files) {
                                                tasks.add(MigrationTask.builder()
                                                        .id(UUID.randomUUID())
                                                        .batchId(batchId)
                                                        .userId(userId)
                                                        .fileId(file.getId())
                                                        .fileName(truncateFileName(file.getOriginalFileName()))
                                                        .sourceProvider(file.getProvider())
                                                        .targetProvider("GOOGLE_DRIVE")
                                                        .targetExternalAccountId(targetAccountId)
                                                        .deleteSource(deleteSource)
                                                        .status(MigrationStatus.PENDING)
                                                        .progress(0.0)
                                                        .targetFolderId(newGoogleFolderId)
                                                        .updatedAt(Instant.now())
                                                        .build());
                                            }
                                            return migrationTaskRepository.saveAll(tasks)
                                                    .collectList()
                                                    .flatMap(savedTasks -> runMigrationTasksInBackground(savedTasks, files));
                                        });

                                Mono<Void> migrateSubfolders = folderRepository.findByUserIdAndParentId(userId, localFolderId)
                                        .flatMap(subFolder -> {
                                            UUID subFolderTaskId = UUID.randomUUID();
                                            MigrationTask subTask = MigrationTask.builder()
                                                    .id(subFolderTaskId)
                                                    .batchId(batchId)
                                                    .userId(userId)
                                                    .fileId(subFolder.getId())
                                                    .fileName(truncateFileName("[Folder] " + subFolder.getName()))
                                                    .sourceProvider("STORAGE_NODE")
                                                    .targetProvider("GOOGLE_DRIVE")
                                                    .targetExternalAccountId(targetAccountId)
                                                    .deleteSource(deleteSource)
                                                    .status(MigrationStatus.RUNNING)
                                                    .progress(0.0)
                                                    .updatedAt(Instant.now())
                                                    .build();
                                            return migrationTaskRepository.save(subTask)
                                                    .then(migrateFolderRecursive(userId, batchId, subFolder.getId(), newGoogleFolderId, targetAccountId, deleteSource, subFolderTaskId))
                                                    .then(migrationService.updateTaskStatus(subFolderTaskId, MigrationStatus.SUCCESS, 100.0, null))
                                                    .onErrorResume(err -> migrationService.updateTaskStatus(subFolderTaskId, MigrationStatus.FAILED, 0.0, err.getMessage()));
                                        })
                                        .then();

                                Mono<Void> deleteSelf = Mono.empty();
                                if (deleteSource) {
                                    deleteSelf = folderRepository.deleteById(localFolderId).then();
                                }

                                return migrateFiles.then(migrateSubfolders).then(deleteSelf);
                            });
                });
    }

    private Mono<Void> migrateFolderFromGoogleDriveToGoogleDriveRecursive(
            Long userId,
            UUID batchId,
            String gDriveFolderId,
            String parentGoogleFolderId,
            Long sourceAccountId,
            Long destAccountId,
            boolean deleteSource,
            UUID folderTaskId) {

        return googleDriveClient.getFileName(sourceAccountId, gDriveFolderId)
                .flatMap(folderName -> {
                    return googleDriveClient.createFolder(destAccountId, folderName, parentGoogleFolderId)
                            .flatMap(newGoogleFolderId -> {
                                return googleDriveClient.listFilesAndFolders(sourceAccountId, gDriveFolderId)
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
                                                            .fileName(truncateFileName("[Folder] " + itemName))
                                                            .sourceProvider("GOOGLE_DRIVE")
                                                            .targetProvider("GOOGLE_DRIVE")
                                                            .targetExternalAccountId(destAccountId)
                                                            .deleteSource(deleteSource)
                                                            .status(MigrationStatus.RUNNING)
                                                            .progress(0.0)
                                                            .updatedAt(Instant.now())
                                                            .build();
                                                    tasks.add(migrationTaskRepository.save(subTask)
                                                            .then(migrateFolderFromGoogleDriveToGoogleDriveRecursive(
                                                                    userId,
                                                                    batchId,
                                                                    itemId,
                                                                    newGoogleFolderId,
                                                                    sourceAccountId,
                                                                    destAccountId,
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
                                                            .externalAccountId(sourceAccountId)
                                                            .build();

                                                    filesToMigrate.add(file);

                                                    migrationTasks.add(MigrationTask.builder()
                                                            .id(UUID.randomUUID())
                                                            .batchId(batchId)
                                                            .userId(userId)
                                                            .fileId(fileId)
                                                            .fileName(truncateFileName(itemName))
                                                            .sourceProvider("GOOGLE_DRIVE")
                                                            .targetProvider("GOOGLE_DRIVE")
                                                            .targetExternalAccountId(destAccountId)
                                                            .deleteSource(deleteSource)
                                                            .status(MigrationStatus.PENDING)
                                                            .progress(0.0)
                                                            .targetFolderId(newGoogleFolderId)
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
                                                deleteSelf = googleDriveClient.deleteFile(sourceAccountId, gDriveFolderId).then();
                                            }

                                            return processFiles.then(processSubfolders).then(deleteSelf);
                                        });
                            });
                });
    }

    private String truncateFileName(String name) {
        if (name == null) return "";
        return name.substring(0, Math.min(name.length(), 255));
    }
}
