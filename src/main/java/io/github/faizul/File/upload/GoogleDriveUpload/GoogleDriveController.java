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
public class GoogleDriveController {

    private final UploadGoogleDriveServiceImp uploadService;

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
}
