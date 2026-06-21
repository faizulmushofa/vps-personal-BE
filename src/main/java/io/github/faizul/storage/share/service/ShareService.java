package io.github.faizul.storage.share.service;

import io.github.faizul.storage.file.dtos.*;
import io.github.faizul.storage.file.dtos.FileResponse;
import io.github.faizul.storage.file.dtos.ShareFileRequest;
import io.github.faizul.storage.file.dtos.ShareFileResponse;
import io.github.faizul.storage.file.dtos.SharedByMeResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ShareService {
    Mono<ShareFileResponse> shareFile(String fileId, ShareFileRequest request);
    Mono<Void> unshareFile(String fileId, Long targetUserId);
    Mono<Void> unshareFile(Long shareId);
    Flux<FileResponse> getSharedWithMe();
    Mono<Boolean> hasReadAccess(String fileId, Long userId);
    Mono<FileResponse> getPublicFileInfo(String shareToken);
    Flux<byte[]> downloadPublicFile(String shareToken);
    Flux<SharedByMeResponse> getSharedByMe();
}
