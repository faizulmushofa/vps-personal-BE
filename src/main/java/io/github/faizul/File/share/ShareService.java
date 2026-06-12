package io.github.faizul.File.share;

import io.github.faizul.File.dtos.*;
import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.File.dtos.ShareFileRequest;
import io.github.faizul.File.dtos.ShareFileResponse;
import io.github.faizul.File.dtos.SharedByMeResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ShareService {
    Mono<ShareFileResponse> shareFile(UUID fileId, ShareFileRequest request);
    Mono<Void> unshareFile(UUID fileId, Long targetUserId);
    Mono<Void> unshareFile(Long shareId);
    Flux<FileResponse> getSharedWithMe();
    Mono<Boolean> hasReadAccess(UUID fileId, Long userId);
    Mono<FileResponse> getPublicFileInfo(String shareToken);
    Flux<byte[]> downloadPublicFile(String shareToken);
    Flux<SharedByMeResponse> getSharedByMe();
}
