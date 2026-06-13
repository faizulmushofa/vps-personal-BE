package io.github.faizul.File.upload.GoogleDriveUpload;

import io.github.faizul.File.dtos.InitRequest;
import io.github.faizul.File.dtos.InitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController("googleDriveUploadController")
@RequestMapping("api/google-drive/upload")
@RequiredArgsConstructor
public class GoogleDriveUploadController {

    private final UploadGoogleDriveServiceImp uploadService;
    private final io.github.faizul.security.filter.CurrentUserContext currentUserContext;
    private final io.github.faizul.activity.UserActivityService userActivityService;

    @PostMapping("/init")
    public Mono<ResponseEntity<InitResponse>> init(@RequestBody InitRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> uploadService.create(request)
                        .flatMap(response -> userActivityService.log(userId, "UPLOAD_INIT_GD", "Mengunggah berkas ke Google Drive: " + request.fileName(), exchange)
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
        return uploadService.handleChunkUpload(id, index, filePart)
                .thenReturn(ResponseEntity.accepted().build());
    }

    @PostMapping("/{id}/cancel")
    public Mono<ResponseEntity<Void>> cancelUpload(@PathVariable UUID id) {
        return uploadService.cancelUpload(id)
                .thenReturn(ResponseEntity.ok().build());
    }
}
