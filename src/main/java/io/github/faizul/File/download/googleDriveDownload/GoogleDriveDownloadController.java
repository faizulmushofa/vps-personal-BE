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

import java.util.UUID;

@RestController
@RequestMapping("api/google-drive/download")
public class GoogleDriveDownloadController {

    private final DownloadService downloadService;
    private final FileRepository fileRepository;
    private final io.github.faizul.security.filter.CurrentUserContext currentUserContext;
    private final io.github.faizul.activity.UserActivityService userActivityService;

    public GoogleDriveDownloadController(
            @Qualifier("googleDriveDownloadService") DownloadService downloadService,
            FileRepository fileRepository,
            io.github.faizul.security.filter.CurrentUserContext currentUserContext,
            io.github.faizul.activity.UserActivityService userActivityService) {
        this.downloadService = downloadService;
        this.fileRepository = fileRepository;
        this.currentUserContext = currentUserContext;
        this.userActivityService = userActivityService;
    }

    @PostMapping("/init")
    public Mono<ResponseEntity<DownloadInitResponse>> init(@RequestBody DownloadInitRequest request) {
        return downloadService.init(request)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{fileId}/stream")
    public Mono<ResponseEntity<Flux<byte[]>>> streamFile(@PathVariable UUID fileId, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> fileRepository.findById(fileId)
                        .flatMap(file -> userActivityService.log(userId, "DOWNLOAD_GD", "Mengunduh berkas Google Drive: " + file.getOriginalFileName(), exchange)
                                .thenReturn(ResponseEntity.ok()
                                        .header("Content-Disposition", "attachment; filename=\"" + file.getOriginalFileName() + "\"")
                                        .header("Content-Length", String.valueOf(file.getSize()))
                                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                        .body(downloadService.streamFile(fileId)))
                        )
                )
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
