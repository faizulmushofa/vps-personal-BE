package io.github.faizul.File.download;

import io.github.faizul.File.dtos.*;
import io.github.faizul.File.core.*;

import io.github.faizul.File.dtos.DownloadInitRequest;
import io.github.faizul.File.dtos.DownloadInitResponse;
import io.github.faizul.File.dtos.DownloadStatusResponse;
import io.github.faizul.File.core.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("api/files/download")
@RequiredArgsConstructor
public class DownloadController {

    private final DownloadService downloadService;
    private final FileRepository fileRepository;

    @PostMapping("/init")
    public Mono<ResponseEntity<DownloadInitResponse>> init(@RequestBody DownloadInitRequest request) {
        return downloadService.init(request)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{fileId}/stream")
    public Mono<ResponseEntity<Flux<byte[]>>> streamFile(@PathVariable UUID fileId) {
        return fileRepository.findById(fileId)
                .map(file -> ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"" + file.getOriginalFileName() + "\"")
                        .header("Content-Length", String.valueOf(file.getSize()))
                        .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                        .body(downloadService.streamFile(fileId)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{fileId}/stream/chunked")
    public Mono<ResponseEntity<Flux<byte[]>>> streamFileChunked(
            @PathVariable UUID fileId,
            @RequestParam(defaultValue = "262144") int chunkSize
    ) {
        return fileRepository.findById(fileId)
                .map(file -> ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"" + file.getOriginalFileName() + "\"")
                        .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                        .body(downloadService.streamFileChunked(fileId, chunkSize)))
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

