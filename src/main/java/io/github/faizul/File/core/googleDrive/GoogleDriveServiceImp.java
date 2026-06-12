package io.github.faizul.File.core.googleDrive;

import io.github.faizul.File.core.File;
import io.github.faizul.File.core.FileRepository;
import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.File.dtos.UserStorageResponse;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.User.externalAccount.ExternalAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service("googleDriveCoreService")
@Transactional
@Slf4j
public class GoogleDriveServiceImp {

    private final FileRepository fileRepository;
    private final CurrentUserContext currentUserContext;
    private final GoogleDriveClient googleDriveClient;
    private final ExternalAccountRepository externalAccountRepository;
    private final Scheduler googleSyncScheduler;

    public GoogleDriveServiceImp(
            FileRepository fileRepository,
            CurrentUserContext currentUserContext,
            GoogleDriveClient googleDriveClient,
            ExternalAccountRepository externalAccountRepository,
            @Qualifier("googleSyncScheduler") Scheduler googleSyncScheduler) {
        this.fileRepository = fileRepository;
        this.currentUserContext = currentUserContext;
        this.googleDriveClient = googleDriveClient;
        this.externalAccountRepository = externalAccountRepository;
        this.googleSyncScheduler = googleSyncScheduler;
    }

    public Flux<FileResponse> getFiles(Long externalAccountId) {
        return currentUserContext.getUserId()
                .flatMapMany(userId -> fileRepository.findByUserId(userId))
                .filter(file -> "GOOGLE_DRIVE".equals(file.getProvider()) &&
                        (externalAccountId == null || externalAccountId.equals(file.getExternalAccountId())))
                .map(file -> new FileResponse(
                        file.getId(),
                        file.getOriginalFileName(),
                        file.getSize(),
                        file.getCreatedAt(),
                        file.getProvider(),
                        file.getExternalAccountId(),
                        null
                ));
    }

    public Mono<String> deleteFile(UUID uuid) {
        return currentUserContext.getUserId()
                .flatMap(userId -> fileRepository.findById(uuid)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("Berkas tidak ditemukan!")))
                        .flatMap(file -> {
                            if (!file.getUserId().equals(userId)) {
                                return Mono.error(new org.springframework.security.access.AccessDeniedException("Anda tidak memiliki akses untuk menghapus berkas Google Drive ini"));
                            }
                            return googleDriveClient.deleteFile(file.getExternalAccountId(), file.getStorageName())
                                    .thenReturn("")
                                    .onErrorResume(e -> {
                                        System.err.println("Warning: Gagal menghapus file dari Google Drive API: " + e.getMessage());
                                        return Mono.just(e.getMessage());
                                    })
                                    .flatMap(warning -> fileRepository.deleteById(uuid)
                                            .thenReturn(warning)
                                    );
                        })
                );
    }

    public Mono<UserStorageResponse> getStorage(Long externalAccountId) {
        return currentUserContext.getUserId()
                .flatMap(userId -> externalAccountRepository.findByIdAndUserId(externalAccountId, userId)
                        .flatMap(account -> googleDriveClient.getAboutSpace(externalAccountId)
                                .map(quotaMap -> {
                                    long gUsed = 0L;
                                    long gLimit = 0L;
                                    if (quotaMap.get("usage") != null) {
                                        gUsed = Long.parseLong(quotaMap.get("usage").toString());
                                    }
                                    if (quotaMap.get("limit") != null) {
                                        gLimit = Long.parseLong(quotaMap.get("limit").toString());
                                    }
                                    return new UserStorageResponse(
                                            0L, // local usedBytes, not relevant here
                                            0L, // local quotaBytes, not relevant here
                                            true,
                                            gUsed,
                                            gLimit
                                    );
                                })
                        )
                        .defaultIfEmpty(new UserStorageResponse(
                                0L,
                                0L,
                                false,
                                0L,
                                0L
                        ))
                );
    }

    public Mono<Void> syncGoogleDrive(Long externalAccountId) {
        return currentUserContext.getUserId()
                .flatMap(userId -> externalAccountRepository.findByIdAndUserId(externalAccountId, userId)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("Akun Google Drive tidak ditemukan")))
                        .flatMap(account -> {
                            log.info("Memulai sinkronisasi Google Drive untuk akun: {}", account.getEmail());
                            return googleDriveClient.listFiles(externalAccountId)
                                    .flatMap(googleFiles -> {
                                        log.info("Berhasil mengambil {} berkas dari Google Drive API", googleFiles.size());
                                        return fileRepository.findByUserId(userId)
                                                .filter(file -> "GOOGLE_DRIVE".equals(file.getProvider()) &&
                                                        externalAccountId.equals(file.getExternalAccountId()))
                                                .collectList()
                                                .map(localGoogleFiles -> calculateSyncDiff(userId, externalAccountId, googleFiles, localGoogleFiles))
                                                .flatMap(this::applyDatabaseSyncChanges);
                                    });
                        })
                )
                .subscribeOn(googleSyncScheduler);
    }

    private record SyncDiff(List<File> filesToSave, List<UUID> idsToDelete) {}

    private SyncDiff calculateSyncDiff(Long userId, Long externalAccountId, List<Map<String, Object>> googleFiles, List<File> localGoogleFiles) {
        log.info("Daftar berkas Google Drive lokal di database: {} berkas", localGoogleFiles.size());
        Map<String, File> localMap = localGoogleFiles.stream()
                .collect(Collectors.toMap(File::getStorageName, f -> f));

        List<File> filesToSave = new ArrayList<>();
        List<UUID> idsToDelete = new ArrayList<>();
        Set<String> seenGoogleIds = new HashSet<>();

        for (Map<String, Object> gFile : googleFiles) {
            String gId = (String) gFile.get("id");
            String gName = (String) gFile.get("name");
            long gSize = 0L;
            if (gFile.get("size") != null) {
                gSize = Long.parseLong(gFile.get("size").toString());
            }
            seenGoogleIds.add(gId);

            if (localMap.containsKey(gId)) {
                File existing = localMap.get(gId);
                if (!existing.getOriginalFileName().equals(gName) || existing.getSize() != gSize) {
                    log.info("Memperbarui nama/ukuran file Google Drive lama: {} (ID: {})", gName, gId);
                    existing.setOriginalFileName(gName);
                    existing.setSize(gSize);
                    filesToSave.add(existing);
                }
            } else {
                log.info("Menambahkan file Google Drive baru: {} (ID: {})", gName, gId);
                File newFile = File.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .originalFileName(gName)
                        .storageName(gId)
                        .size(gSize)
                        .provider("GOOGLE_DRIVE")
                        .externalAccountId(externalAccountId)
                        .build();
                filesToSave.add(newFile);
            }
        }

        for (File localFile : localGoogleFiles) {
            if (!seenGoogleIds.contains(localFile.getStorageName())) {
                log.info("Menghapus berkas Google Drive lokal yang tidak ada lagi di awan: {} (ID: {})", localFile.getOriginalFileName(), localFile.getStorageName());
                idsToDelete.add(localFile.getId());
            }
        }

        return new SyncDiff(filesToSave, idsToDelete);
    }

    private Mono<Void> applyDatabaseSyncChanges(SyncDiff diff) {
        if (diff.filesToSave().isEmpty() && diff.idsToDelete().isEmpty()) {
            log.info("Sinkronisasi selesai. Tidak ada perubahan yang perlu disimpan.");
            return Mono.empty();
        }

        log.info("Menjalankan sinkronisasi database: {} disimpan, {} dihapus secara berurutan", diff.filesToSave().size(), diff.idsToDelete().size());

        Mono<Void> deleteFlow = Flux.fromIterable(diff.idsToDelete())
                .concatMap(id -> fileRepository.deleteById(id))
                .then();

        Mono<Void> saveFlow = Flux.fromIterable(diff.filesToSave())
                .concatMap(file -> fileRepository.save(file))
                .then();

        return deleteFlow.then(saveFlow);
    }
}
