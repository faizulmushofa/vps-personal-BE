package io.github.faizul.storage.upload.gdrive.controller;

import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.storage.file.dtos.InitRequest;
import io.github.faizul.storage.file.dtos.InitResponse;
import io.github.faizul.storage.upload.gdrive.service.GoogleDriveUploadService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController("googleDriveUploadController")
@RequestMapping("api/google-drive/upload")
@RequiredArgsConstructor
public class GoogleDriveUploadController {

    private final GoogleDriveUploadService uploadService;
    private final CurrentUserContext currentUserContext;

    @PostMapping("/init")
    public Mono<ResponseEntity<InitResponse>> init(@RequestBody InitRequest request) {
        return uploadService.create(request)
                .map(response -> ResponseEntity.ok().body(response));
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
