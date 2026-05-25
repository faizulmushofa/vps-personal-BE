package io.github.faizul.UploadUnit.Implementation;

import io.github.faizul.infra.config.*;

import io.github.faizul.infra.config.StorageConfig;
import io.github.faizul.UploadUnit.IOCleaningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class IoCleaningServiceImp implements IOCleaningService {

    private final Scheduler fileCleanupScheduler;
    private final StorageConfig storageConfig;

    @Override
    public Mono<Void> cleanupBatch(Long userId, UUID fileId, int startChunk, int endChunk) {
        return Mono.<Void>fromRunnable(() -> {
            Path dir = storageConfig.tempDir(userId, fileId);
            for (int i = startChunk; i <= endChunk; i++) {
                Path chunkFile = dir.resolve("chunk-" + i);
                try {
                    Files.deleteIfExists(chunkFile);
                    log.debug("Deleted chunk file: {}", chunkFile);
                } catch (Exception e) {
                    log.warn("Failed to delete chunk file {}: {}", chunkFile, e.getMessage());
                }
            }
        }).subscribeOn(fileCleanupScheduler);
    }

    @Override
    public Mono<Void> cleanupTempFiles(Long userId, UUID fileId) {
        return Mono.<Void>fromRunnable(() -> {
            Path dir = storageConfig.tempDir(userId, fileId);
            try {
                FileSystemUtils.deleteRecursively(dir);
                log.info("Deleted temp directory for file: {}", fileId);
            } catch (Exception e) {
                log.warn("Failed to delete temp directory {}: {}", dir, e.getMessage());
            }
        }).subscribeOn(fileCleanupScheduler);
    }
}
