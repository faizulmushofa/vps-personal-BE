package io.github.faizul.UploadUnit.Implementation;

import io.github.faizul.UploadUnit.IOCleaningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Component
public class IoCleaningServiceImp implements IOCleaningService {

    @Override
    public Mono<Void> cleanupBatch(UUID fileId, int startChunk, int endChunk) {
        return Mono.<Void>fromRunnable(() -> {
            Path dir = Paths.get("temp", fileId.toString());
            for (int i = startChunk; i <= endChunk; i++) {
                Path chunkFile = dir.resolve("chunk-" + i);
                try {
                    Files.deleteIfExists(chunkFile);
                    log.debug("Deleted chunk file: {}", chunkFile);
                } catch (Exception e) {
                    log.warn("Failed to delete chunk file {}: {}", chunkFile, e.getMessage());
                }
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> cleanupTempFiles(UUID fileId) {
        return Mono.<Void>fromRunnable(() -> {
            Path dir = Paths.get("temp", fileId.toString());
            try {
                FileSystemUtils.deleteRecursively(dir);
                log.info("Deleted temp directory for file: {}", fileId);
            } catch (Exception e) {
                log.warn("Failed to delete temp directory {}: {}", dir, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
