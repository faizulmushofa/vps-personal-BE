package io.github.faizul.preview;

import io.github.faizul.File.core.FileService;
import io.github.faizul.File.download.DownloadService;
import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.File.share.ShareService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implementasi PreviewService yang TIDAK meng-inject repository apapun.
 * Semua akses data dilakukan melalui service-level yang sudah ada:
 * - FileService (metadata + kontrol akses file pribadi)
 * - DownloadService (streaming byte untuk file pribadi)
 * - ShareService (metadata + streaming byte untuk file publik)
 */
@Service
public class PreviewServiceImpl implements PreviewService {

    private final FileService fileService;
    private final DownloadService storageNodeDownloadService;
    private final DownloadService googleDriveDownloadService;
    private final ShareService storageNodeShareService;
    private final ShareService googleDriveShareService;

    public PreviewServiceImpl(
            FileService fileService,
            @Qualifier("storageNodeDownloadService") DownloadService storageNodeDownloadService,
            @Qualifier("googleDriveDownloadService") DownloadService googleDriveDownloadService,
            @Qualifier("storageNodeShareService") ShareService storageNodeShareService,
            @Qualifier("googleDriveShareService") ShareService googleDriveShareService) {
        this.fileService = fileService;
        this.storageNodeDownloadService = storageNodeDownloadService;
        this.googleDriveDownloadService = googleDriveDownloadService;
        this.storageNodeShareService = storageNodeShareService;
        this.googleDriveShareService = googleDriveShareService;
    }

    @Override
    public Mono<PreviewResult> previewPrivateFile(UUID fileId) {
        return fileService.findByUUID(fileId)
                .map(file -> {
                    String contentType = resolveContentType(file.originalFileName());
                    DownloadService downloadService = resolveDownloadService(file.provider());
                    return new PreviewResult(
                            file.originalFileName(),
                            file.size(),
                            contentType,
                            downloadService.streamFile(fileId)
                    );
                });
    }

    @Override
    public Mono<PreviewResult> previewPublicFile(String shareToken, String provider) {
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
