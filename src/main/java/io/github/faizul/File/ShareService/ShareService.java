package io.github.faizul.File.ShareService;

import io.github.faizul.File.Dtos.FileResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ShareService {
    Mono<Void> shareFile(UUID fileId, String targetEmail);
    Mono<Void> unshareFile(UUID fileId, Long targetUserId);
    Flux<FileResponse> getSharedWithMe();
    Mono<Boolean> hasReadAccess(UUID fileId, Long userId);
}
