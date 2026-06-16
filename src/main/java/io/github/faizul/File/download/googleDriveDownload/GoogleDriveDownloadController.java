package io.github.faizul.File.download.googleDriveDownload;

import io.github.faizul.File.core.FileRepository;
import io.github.faizul.File.download.DownloadService;
import io.github.faizul.File.dtos.DownloadInitRequest;
import io.github.faizul.File.dtos.DownloadInitResponse;
import io.github.faizul.File.dtos.DownloadStatusResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import io.github.faizul.User.externalAccount.ExternalAccountRepository;
import io.github.faizul.File.core.googleDrive.GoogleDriveClient;
import java.util.UUID;

@RestController
@RequestMapping("api/google-drive/download")
public class GoogleDriveDownloadController {

    private final DownloadService downloadService;
    private final FileRepository fileRepository;
    private final io.github.faizul.security.filter.CurrentUserContext currentUserContext;
    private final io.github.faizul.activity.UserActivityService userActivityService;
    private final ExternalAccountRepository externalAccountRepository;
    private final GoogleDriveClient googleDriveClient;

    public GoogleDriveDownloadController(
            @Qualifier("googleDriveDownloadService") DownloadService downloadService,
            FileRepository fileRepository,
            io.github.faizul.security.filter.CurrentUserContext currentUserContext,
            io.github.faizul.activity.UserActivityService userActivityService,
            ExternalAccountRepository externalAccountRepository,
            GoogleDriveClient googleDriveClient) {
        this.downloadService = downloadService;
        this.fileRepository = fileRepository;
        this.currentUserContext = currentUserContext;
        this.userActivityService = userActivityService;
        this.externalAccountRepository = externalAccountRepository;
        this.googleDriveClient = googleDriveClient;
    }

    @PostMapping("/init")
    public Mono<ResponseEntity<DownloadInitResponse>> init(@RequestBody DownloadInitRequest request) {
        return downloadService.init(request)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{fileId}/stream")
    public Mono<ResponseEntity<Flux<byte[]>>> streamFile(
            @PathVariable String fileId,
            @RequestParam(required = false) Long externalAccountId,
            org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> {
                    boolean isUuid = false;
                    UUID uuid = null;
                    try {
                        uuid = UUID.fromString(fileId);
                        isUuid = true;
                    } catch (IllegalArgumentException e) {
                        // Not a UUID
                    }

                    if (isUuid) {
                        final UUID finalUuid = uuid;
                        return fileRepository.findById(finalUuid)
                                .flatMap(file -> userActivityService.log(userId, "DOWNLOAD_GD", "Mengunduh berkas Google Drive: " + file.getOriginalFileName(), exchange)
                                        .thenReturn(ResponseEntity.ok()
                                                .header("Content-Disposition", "attachment; filename=\"" + file.getOriginalFileName() + "\"")
                                                .header("Content-Length", String.valueOf(file.getSize()))
                                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                                .body(downloadService.streamFile(finalUuid)))
                                );
                    } else {
                        if (externalAccountId == null) {
                            return Mono.error(new IllegalArgumentException("externalAccountId is required for non-UUID files"));
                        }
                        return externalAccountRepository.findByIdAndUserId(externalAccountId, userId)
                                .switchIfEmpty(Mono.error(new SecurityException("Akses ditolak: Akun eksternal tidak valid")))
                                .flatMap(account -> googleDriveClient.getFileMetadata(externalAccountId, fileId)
                                        .flatMap(metadata -> {
                                            String name = (String) metadata.get("name");
                                            Object sizeObj = metadata.get("size");
                                            long size = 0;
                                            if (sizeObj instanceof Number) {
                                                size = ((Number) sizeObj).longValue();
                                            } else if (sizeObj instanceof String) {
                                                size = Long.parseLong((String) sizeObj);
                                            }
                                            final long finalSize = size;
                                            return userActivityService.log(userId, "DOWNLOAD_GD", "Mengunduh berkas Google Drive: " + name, exchange)
                                                    .thenReturn(ResponseEntity.ok()
                                                            .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                                                            .header("Content-Length", String.valueOf(finalSize))
                                                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                                            .body(googleDriveClient.downloadFile(externalAccountId, fileId)));
                                        })
                                );
                    }
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{fileId}/status")
    public Mono<ResponseEntity<DownloadStatusResponse>> getStatus(@PathVariable UUID fileId) {
        return downloadService.getStatus(fileId)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{fileId}/cancel")
    public Mono<ResponseEntity<Void>> cancel(@PathVariable UUID fileId) {
        return downloadService.cancel(fileId)
                .thenReturn(ResponseEntity.ok().build());
    }
}
