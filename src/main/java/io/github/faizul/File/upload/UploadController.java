package io.github.faizul.File.upload;

import io.github.faizul.File.dtos.*;
import io.github.faizul.File.core.*;

import io.github.faizul.File.dtos.InitRequest;
import io.github.faizul.File.dtos.InitResponse;
import io.github.faizul.UploadUnit.UploadCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/files")
public class UploadController {

    private final UploadService uploadService;
    private final UploadCoordinator uploadCoordinator;

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

}
