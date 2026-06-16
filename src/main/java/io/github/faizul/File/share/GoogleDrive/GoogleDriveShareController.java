package io.github.faizul.File.share.GoogleDrive;

import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.File.dtos.ShareFileRequest;
import io.github.faizul.File.dtos.ShareFileResponse;
import io.github.faizul.File.share.ShareService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("api/google-drive/share")
public class GoogleDriveShareController {

    private final ShareService shareService;
    private final io.github.faizul.security.filter.CurrentUserContext currentUserContext;
    private final io.github.faizul.activity.UserActivityService userActivityService;
    private final io.github.faizul.File.core.FileRepository fileRepository;

    public GoogleDriveShareController(
            @Qualifier("googleDriveShareService") ShareService shareService,
            io.github.faizul.security.filter.CurrentUserContext currentUserContext,
            io.github.faizul.activity.UserActivityService userActivityService,
            io.github.faizul.File.core.FileRepository fileRepository) {
        this.shareService = shareService;
        this.currentUserContext = currentUserContext;
        this.userActivityService = userActivityService;
        this.fileRepository = fileRepository;
    }

    @PostMapping("/{fileId}")
    public Mono<ResponseEntity<ShareFileResponse>> shareFile(@PathVariable String fileId, @RequestBody ShareFileRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> shareService.shareFile(fileId, request)
                        .flatMap(response -> {
                            Mono<String> getFileNameMono;
                            try {
                                UUID uuid = UUID.fromString(fileId);
                                getFileNameMono = fileRepository.findById(uuid)
                                        .map(file -> file.getOriginalFileName())
                                        .defaultIfEmpty("Berkas Google Drive");
                            } catch (IllegalArgumentException e) {
                                getFileNameMono = fileRepository.findByStorageNameAndProvider(fileId, "GOOGLE_DRIVE")
                                        .map(file -> file.getOriginalFileName())
                                        .defaultIfEmpty("Berkas Google Drive");
                            }
                            
                            return getFileNameMono
                                    .flatMap(fileName -> userActivityService.log(userId, "SHARE_FILE_GD", "Membagikan berkas Google Drive: " + fileName, exchange))
                                    .thenReturn(response);
                        })
                )
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{fileId}/{userId}")
    public Mono<ResponseEntity<Void>> unshareFile(@PathVariable String fileId, @PathVariable Long userId, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(adminId -> shareService.unshareFile(fileId, userId)
                        .then(Mono.defer(() -> {
                            Mono<String> getFileNameMono;
                            try {
                                UUID uuid = UUID.fromString(fileId);
                                getFileNameMono = fileRepository.findById(uuid)
                                        .map(file -> file.getOriginalFileName())
                                        .defaultIfEmpty("Berkas Google Drive");
                            } catch (IllegalArgumentException e) {
                                getFileNameMono = fileRepository.findByStorageNameAndProvider(fileId, "GOOGLE_DRIVE")
                                        .map(file -> file.getOriginalFileName())
                                        .defaultIfEmpty("Berkas Google Drive");
                            }
                            return getFileNameMono.flatMap(fileName -> 
                                userActivityService.log(adminId, "UNSHARE_FILE_GD", "Membatalkan share berkas Google Drive " + fileName + " untuk user ID: " + userId, exchange)
                            );
                        }))
                )
                .thenReturn(ResponseEntity.noContent().build());
    }

    @GetMapping("/shared-with-me")
    public Flux<FileResponse> getSharedWithMe() {
        return shareService.getSharedWithMe();
    }

    @GetMapping("/public/info/{shareToken}")
    public Mono<ResponseEntity<FileResponse>> getPublicFileInfo(@PathVariable String shareToken) {
        return shareService.getPublicFileInfo(shareToken)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/public/download/{shareToken}")
    public Mono<ResponseEntity<Flux<byte[]>>> downloadPublicFile(
            @PathVariable String shareToken,
            @RequestParam(value = "download", required = false, defaultValue = "false") Boolean download,
            org.springframework.web.server.ServerWebExchange exchange) {
        return shareService.getPublicFileInfo(shareToken)
                .flatMap(file -> {
                    String disposition = Boolean.TRUE.equals(download)
                            ? "attachment; filename=\"" + file.originalFileName() + "\""
                            : "inline; filename=\"" + file.originalFileName() + "\"";

                    String contentType = org.springframework.http.MediaTypeFactory.getMediaType(file.originalFileName())
                            .map(org.springframework.http.MediaType::toString)
                            .orElse("application/octet-stream");

                    return userActivityService.log(null, "DOWNLOAD_SHARED_PUBLIC_GD", "Mengunduh berkas publik Google Drive: " + file.originalFileName() + " dengan token: " + shareToken, exchange)
                            .thenReturn(ResponseEntity.ok()
                                    .header("Content-Disposition", disposition)
                                    .header("Content-Length", String.valueOf(file.size()))
                                    .contentType(MediaType.parseMediaType(contentType))
                                    .body(shareService.downloadPublicFile(shareToken)));
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/shared-by-me")
    public Flux<io.github.faizul.File.dtos.SharedByMeResponse> getSharedByMe() {
        return shareService.getSharedByMe();
    }

    @DeleteMapping("/cancel/{shareId}")
    public Mono<ResponseEntity<Void>> unshareFileById(@PathVariable Long shareId, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> shareService.unshareFile(shareId)
                        .then(userActivityService.log(userId, "CANCEL_SHARE_GD", "Membatalkan share berkas Google Drive dengan Share ID: " + shareId, exchange))
                )
                .thenReturn(ResponseEntity.noContent().build());
    }
}
