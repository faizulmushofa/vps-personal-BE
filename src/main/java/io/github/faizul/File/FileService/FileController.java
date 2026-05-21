package io.github.faizul.File.FileService;

import io.github.faizul.File.Dtos.FileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;


    @GetMapping("/{id}")
    public Mono<ResponseEntity<FileResponse>> findById(@PathVariable UUID id) {
        return fileService.findByUUID(id)
                .map(response -> ResponseEntity.ok().body(response));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteFile(@PathVariable UUID id) {
        return fileService.deleteByUUID(id)
                .thenReturn(ResponseEntity.noContent().build());
    }

    @GetMapping
    public Flux<FileResponse> getAllByUserId() {
        return fileService.getAllByUserId();
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<FileResponse> getAllForAdmin() {
        return fileService.getAllForAdmin();
    }
}
