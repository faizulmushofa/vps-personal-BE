package io.github.faizul.migration;

import io.github.faizul.File.core.File;
import io.github.faizul.File.core.FileRepository;
import io.github.faizul.setting.AppSettingRepository;
import io.github.faizul.security.filter.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MigrationServiceImp implements MigrationService {

    private final MigrationTaskRepository migrationTaskRepository;
    private final FileRepository fileRepository;
    private final AppSettingRepository appSettingRepository;
    private final CurrentUserContext currentUserContext;
    private final DatabaseClient databaseClient;

    private static final long DEFAULT_MAX_SIZE = 256 * 1024 * 1024L; // 256 MB
    private static final int DEFAULT_DAILY_LIMIT = 3;

    @Override
    public Mono<Map<String, Object>> getMigrationConfig() {
        return Mono.zip(
                appSettingRepository.findByKey("migration.max_file_size_bytes")
                        .map(setting -> Long.parseLong(setting.getValue()))
                        .defaultIfEmpty(DEFAULT_MAX_SIZE),
                appSettingRepository.findByKey("migration.max_daily_limit")
                        .map(setting -> Integer.parseInt(setting.getValue()))
                        .defaultIfEmpty(DEFAULT_DAILY_LIMIT)
        ).map(tuple -> Map.of(
                "maxFileSizeBytes", tuple.getT1(),
                "maxDailyLimit", tuple.getT2()
        ));
    }

    @Override
    public Mono<Map<String, Object>> updateMigrationConfig(Map<String, String> newSettings) {
        return Flux.fromIterable(newSettings.entrySet())
                .flatMap(entry -> appSettingRepository.findByKey(entry.getKey())
                        .flatMap(setting -> {
                            setting.setValue(entry.getValue());
                            return appSettingRepository.save(setting);
                        }))
                .then(getMigrationConfig());
    }

    @Override
    public Mono<Void> validateCommonLimits(Long userId) {
        // 1. Cek limit paralel (apakah ada migrasi sedang berjalan untuk user)
        return migrationTaskRepository.existsByUserIdAndStatus(userId, MigrationStatus.RUNNING)
                .flatMap(hasActive -> {
                    if (hasActive) {
                        return Mono.error(new IllegalStateException("Ada proses migrasi lain yang sedang berjalan. Silakan tunggu hingga selesai."));
                    }

                    // 2. Cek batas harian (midnight boundary)
                    Instant startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
                    return migrationTaskRepository.countByUserIdAndCreatedAtAfter(userId, startOfToday)
                            .flatMap(dailyCount -> {
                                return appSettingRepository.findByKey("migration.max_daily_limit")
                                        .map(setting -> Integer.parseInt(setting.getValue()))
                                        .defaultIfEmpty(DEFAULT_DAILY_LIMIT)
                                        .flatMap(maxLimit -> {
                                            if (dailyCount >= maxLimit) {
                                                return Mono.error(new IllegalArgumentException("Batas harian migrasi terlampaui (Maksimal " + maxLimit + " kali migrasi per hari)."));
                                            }
                                            return Mono.empty();
                                        });
                            });
                }).then();
    }

    @Override
    public Mono<List<File>> validateAndGetSourceFiles(
            List<UUID> fileIds, 
            Long userId, 
            Long maxFileSizeBytes, 
            String targetProvider, 
            Long targetExternalAccountId) {
        return Flux.fromIterable(fileIds)
                .flatMap(fileId -> fileRepository.findById(fileId)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan: " + fileId)))
                        .flatMap(file -> {
                            // Validasi kepemilikan
                            if (!file.getUserId().equals(userId)) {
                                return Mono.error(new org.springframework.security.access.AccessDeniedException("Anda tidak memiliki akses ke berkas ini."));
                            }

                            // Validasi self-migration
                            boolean isSameProvider = file.getProvider().equalsIgnoreCase(targetProvider);
                            boolean isSameAccount = Objects.equals(file.getExternalAccountId(), targetExternalAccountId);
                            if (isSameProvider && isSameAccount) {
                                return Mono.error(new IllegalArgumentException("File " + file.getOriginalFileName() + " sudah berada di penyimpanan target yang sama."));
                            }

                            // Validasi file size
                            if (file.getSize() > maxFileSizeBytes) {
                                return Mono.error(new IllegalArgumentException("Berkas " + file.getOriginalFileName() + " melebihi batas ukuran migrasi premium (" + (maxFileSizeBytes / 1024 / 1024) + " MB)."));
                            }

                            return Mono.just(file);
                        }))
                .collectList();
    }

    @Override
    public Mono<Void> updateTaskProgress(UUID taskId, double progress) {
        return databaseClient.sql("UPDATE migration_tasks SET progress = :progress, updated_at = :now WHERE id = :id")
                .bind("progress", progress)
                .bind("now", Instant.now())
                .bind("id", taskId)
                .then();
    }

    @Override
    public Mono<Void> updateTaskStatus(UUID taskId, MigrationStatus status, double progress, String errorMessage) {
        return databaseClient.sql("UPDATE migration_tasks SET status = :status, progress = :progress, error_message = :err, updated_at = :now WHERE id = :id")
                .bind("status", status.name())
                .bind("progress", progress)
                .bind("err", errorMessage != null ? errorMessage : "")
                .bind("now", Instant.now())
                .bind("id", taskId)
                .then();
    }

    @Override
    public Flux<MigrationTask> getTasks(UUID batchId) {
        if (batchId != null) {
            return migrationTaskRepository.findByBatchId(batchId);
        }
        return currentUserContext.getUserId()
                .flatMapMany(migrationTaskRepository::findByUserId);
    }
}
