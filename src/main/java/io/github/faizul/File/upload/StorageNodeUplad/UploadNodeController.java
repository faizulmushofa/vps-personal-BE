package io.github.faizul.File.upload.StorageNodeUplad;

import io.github.faizul.File.dtos.InitRequest;
import io.github.faizul.File.dtos.InitResponse;
import io.github.faizul.File.upload.UploadService;
import io.github.faizul.UploadUnit.UploadCoordinator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("api/files")
public class UploadNodeController {

    private final UploadService uploadService;
    private final UploadCoordinator uploadCoordinator;
    private final io.github.faizul.security.filter.CurrentUserContext currentUserContext;
    private final io.github.faizul.activity.UserActivityService userActivityService;

    public UploadNodeController(
            @Qualifier("storageNodeUploadService") UploadService uploadService,
            UploadCoordinator uploadCoordinator,
            io.github.faizul.security.filter.CurrentUserContext currentUserContext,
            io.github.faizul.activity.UserActivityService userActivityService) {
        this.uploadService = uploadService;
        this.uploadCoordinator = uploadCoordinator;
        this.currentUserContext = currentUserContext;
        this.userActivityService = userActivityService;
    }

    @PostMapping("/init")
    public Mono<ResponseEntity<InitResponse>> init(@RequestBody InitRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> uploadService.create(request)
                        .flatMap(response -> userActivityService.log(userId, "UPLOAD_INIT", "Mengunggah berkas: " + request.fileName(), exchange)
                                .thenReturn(ResponseEntity.ok().body(response))
                        )
                );
    }
    @PostMapping("/{id}/chunks/{index}")
    public Mono<ResponseEntity<Void>> uploadChunk(
            @PathVariable UUID id,
            @PathVariable int index,
            @RequestPart("file") FilePart filePart
    ) {
        return uploadCoordinator.handleChunkUpload(id, index, filePart)
                .thenReturn(ResponseEntity.accepted().build());
    }

    @PostMapping("/{id}/cancel")
    public Mono<ResponseEntity<Void>> cancelUpload(@PathVariable UUID id) {
        return uploadService.cancelUpload(id)
                .thenReturn(ResponseEntity.ok().build());
    }

}
