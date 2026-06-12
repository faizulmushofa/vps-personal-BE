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

    public UploadNodeController(
            @Qualifier("storageNodeUploadService") UploadService uploadService,
            UploadCoordinator uploadCoordinator) {
        this.uploadService = uploadService;
        this.uploadCoordinator = uploadCoordinator;
    }

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
        return uploadCoordinator.handleChunkUpload(id, index, filePart)
                .thenReturn(ResponseEntity.accepted().build());
    }

    @PostMapping("/{id}/cancel")
    public Mono<ResponseEntity<Void>> cancelUpload(@PathVariable UUID id) {
        return uploadService.cancelUpload(id)
                .thenReturn(ResponseEntity.ok().build());
    }

}
