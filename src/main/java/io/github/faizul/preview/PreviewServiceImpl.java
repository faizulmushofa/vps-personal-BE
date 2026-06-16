package io.github.faizul.preview;

import io.github.faizul.File.core.FileService;
import io.github.faizul.File.core.FileRepository;
import io.github.faizul.File.core.File;
import io.github.faizul.File.download.DownloadService;
import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.File.share.ShareService;
import io.github.faizul.folder.share.FolderSharedService;
import io.github.faizul.Storage.download.DownloadStorageService;
import io.github.faizul.File.core.googleDrive.GoogleDriveClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import io.github.faizul.User.externalAccount.ExternalAccountRepository;
import java.util.UUID;

/**
 * Implementasi PreviewService yang TIDAK meng-inject repository apapun.
 * Semua akses data dilakukan melalui service-level yang sudah ada.
 */
@Service
public class PreviewServiceImpl implements PreviewService {

    private final FileService fileService;
    private final FileRepository fileRepository;
    private final DownloadService storageNodeDownloadService;
    private final DownloadService googleDriveDownloadService;
    private final ShareService storageNodeShareService;
    private final ShareService googleDriveShareService;
    private final FolderSharedService folderSharedService;
    private final DownloadStorageService downloadStorageService;
    private final GoogleDriveClient googleDriveClient;
    private final ExternalAccountRepository externalAccountRepository;

    public PreviewServiceImpl(
            FileService fileService,
            FileRepository fileRepository,
            @Qualifier("storageNodeDownloadService") DownloadService storageNodeDownloadService,
            @Qualifier("googleDriveDownloadService") DownloadService googleDriveDownloadService,
            @Qualifier("storageNodeShareService") ShareService storageNodeShareService,
            @Qualifier("googleDriveShareService") ShareService googleDriveShareService,
            FolderSharedService folderSharedService,
            DownloadStorageService downloadStorageService,
            GoogleDriveClient googleDriveClient,
            ExternalAccountRepository externalAccountRepository) {
        this.fileService = fileService;
        this.fileRepository = fileRepository;
        this.storageNodeDownloadService = storageNodeDownloadService;
        this.googleDriveDownloadService = googleDriveDownloadService;
        this.storageNodeShareService = storageNodeShareService;
        this.googleDriveShareService = googleDriveShareService;
        this.folderSharedService = folderSharedService;
        this.downloadStorageService = downloadStorageService;
        this.googleDriveClient = googleDriveClient;
        this.externalAccountRepository = externalAccountRepository;
    }

    @Override
    public Mono<PreviewResult> previewPrivateFile(Long userId, String fileId, String provider, Long externalAccountId) {
        boolean isUuid = false;
        UUID uuid = null;
        try {
            uuid = UUID.fromString(fileId);
            isUuid = true;
        } catch (IllegalArgumentException e) {
            // not a UUID, must be external (e.g. Google Drive) ID
        }

        if (isUuid && (provider == null || !"GOOGLE_DRIVE".equalsIgnoreCase(provider))) {
            final UUID finalUuid = uuid;
            return fileService.findByUUID(finalUuid)
                    .map(file -> {
                        String contentType = resolveContentType(file.originalFileName());
                        DownloadService downloadService = resolveDownloadService(file.provider());
                        return new PreviewResult(
                                file.originalFileName(),
                                file.size(),
                                contentType,
                                downloadService.streamFile(finalUuid)
                        );
                    });
        } else {
            // Must be Google Drive
            if (isUuid && "GOOGLE_DRIVE".equalsIgnoreCase(provider)) {
                final UUID finalUuid = uuid;
                return fileRepository.findById(finalUuid)
                        .flatMap(file -> {
                            if (externalAccountId == null) {
                                return Mono.error(new IllegalArgumentException("externalAccountId is required for external providers"));
                            }
                            return externalAccountRepository.findByIdAndUserId(externalAccountId, userId)
                                    .switchIfEmpty(Mono.error(new SecurityException("Akses ditolak: Akun eksternal tidak valid")))
                                    .flatMap(account -> googleDriveClient.getFileMetadata(externalAccountId, file.getStorageName())
                                            .map(metadata -> {
                                                String name = (String) metadata.get("name");
                                                Object sizeObj = metadata.get("size");
                                                long size = 0;
                                                if (sizeObj instanceof Number) {
                                                    size = ((Number) sizeObj).longValue();
                                                } else if (sizeObj instanceof String) {
                                                    size = Long.parseLong((String) sizeObj);
                                                }
                                                String mimeType = (String) metadata.get("mimeType");
                                                if (mimeType == null) {
                                                    mimeType = resolveContentType(name);
                                                }
                                                Flux<byte[]> stream = googleDriveClient.downloadFile(externalAccountId, file.getStorageName());
                                                return new PreviewResult(name, size, mimeType, stream);
                                            })
                                    );
                        });
            }

            if (externalAccountId == null) {
                return Mono.error(new IllegalArgumentException("externalAccountId is required for external providers"));
            }
            return externalAccountRepository.findByIdAndUserId(externalAccountId, userId)
                    .switchIfEmpty(Mono.error(new SecurityException("Akses ditolak: Akun eksternal tidak valid")))
                    .flatMap(account -> googleDriveClient.getFileMetadata(externalAccountId, fileId)
                            .map(metadata -> {
                                String name = (String) metadata.get("name");
                                Object sizeObj = metadata.get("size");
                                long size = 0;
                                if (sizeObj instanceof Number) {
                                    size = ((Number) sizeObj).longValue();
                                } else if (sizeObj instanceof String) {
                                    size = Long.parseLong((String) sizeObj);
                                }
                                String mimeType = (String) metadata.get("mimeType");
                                if (mimeType == null) {
                                    mimeType = resolveContentType(name);
                                }
                                Flux<byte[]> stream = googleDriveClient.downloadFile(externalAccountId, fileId);
                                return new PreviewResult(name, size, mimeType, stream);
                            })
                    );
        }
    }

    @Override
    public Mono<PreviewResult> previewPublicFile(String shareToken, String provider, String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            ShareService shareService = resolveShareService(provider);
            return shareService.getPublicFileInfo(shareToken)
                    .map(file -> {
                        String contentType = resolveContentType(file.originalFileName());
                        return new PreviewResult(
                                file.originalFileName(),
                                file.size(),
                                contentType,
                                shareService.downloadPublicFile(shareToken)
                        );
                    });
        }

        // Preview a file inside a shared folder
        return folderSharedService.getSharedFileMetadataPublic(shareToken, fileId)
                .flatMap(file -> folderSharedService.getSharedFolderOwnerId(shareToken)
                        .map(ownerId -> {
                            String contentType = resolveContentType(file.originalFileName());
                            Flux<byte[]> dataStream;
                            if ("GOOGLE_DRIVE".equalsIgnoreCase(file.provider())) {
                                dataStream = googleDriveClient.downloadFile(file.externalAccountId(), fileId);
                            } else {
                                dataStream = downloadStorageService.downloadFile(ownerId, UUID.fromString(fileId))
                                        .map(chunk -> chunk.data());
                            }
                            return new PreviewResult(
                                    file.originalFileName(),
                                    file.size(),
                                    contentType,
                                    dataStream
                            );
                        })
                );
    }

    /**
     * Menentukan MIME type dari nama file menggunakan MediaTypeFactory bawaan Spring.
     */
    private String resolveContentType(String fileName) {
        return MediaTypeFactory.getMediaType(fileName)
                .map(org.springframework.http.MediaType::toString)
                .orElse("application/octet-stream");
    }

    /**
     * Memilih DownloadService yang sesuai berdasarkan provider file.
     */
    private DownloadService resolveDownloadService(String provider) {
        if ("GOOGLE_DRIVE".equalsIgnoreCase(provider)) {
            return googleDriveDownloadService;
        }
        return storageNodeDownloadService;
    }

    /**
     * Memilih ShareService yang sesuai berdasarkan provider string dari URL path.
     */
    private ShareService resolveShareService(String provider) {
        if ("google".equalsIgnoreCase(provider) || "GOOGLE_DRIVE".equalsIgnoreCase(provider)) {
            return googleDriveShareService;
        }
        return storageNodeShareService;
    }
}
