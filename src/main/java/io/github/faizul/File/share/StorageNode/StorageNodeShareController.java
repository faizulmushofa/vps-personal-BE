package io.github.faizul.File.share.StorageNode;

import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.File.dtos.ShareFileRequest;
import io.github.faizul.File.share.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("api/files/share")
@RequiredArgsConstructor
public class StorageNodeShareController {

    private final ShareService shareService;

    @PostMapping("/{fileId}")
    public Mono<ResponseEntity<Void>> shareFile(@PathVariable UUID fileId, @RequestBody ShareFileRequest request) {
        return shareService.shareFile(fileId, request.email())
                .thenReturn(ResponseEntity.ok().build());
    }

    @DeleteMapping("/{fileId}/{userId}")
    public Mono<ResponseEntity<Void>> unshareFile(@PathVariable UUID fileId, @PathVariable Long userId) {
        return shareService.unshareFile(fileId, userId)
                .thenReturn(ResponseEntity.noContent().build());
    }

    @GetMapping("/shared-with-me")
    public Flux<FileResponse> getSharedWithMe() {
        return shareService.getSharedWithMe();
    }
}
