package io.github.faizul.File.FileService;

import io.github.faizul.File.Dtos.FileResponse;
import io.github.faizul.File.Dtos.InitRequest;
import io.github.faizul.File.Dtos.InitResponse;
import io.github.faizul.File.UploadService.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final UploadService uploadService;

    @PostMapping("/init")
    public Mono<ResponseEntity<InitResponse>> init(@RequestBody InitRequest request) {
        return uploadService.create(request)
                .map(response -> ResponseEntity.ok().body(response));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<FileResponse>> findById(@PathVariable UUID id) {
        return fileService.findByUUID(id)
                .map(response -> ResponseEntity.ok().body(response));
    }
}
