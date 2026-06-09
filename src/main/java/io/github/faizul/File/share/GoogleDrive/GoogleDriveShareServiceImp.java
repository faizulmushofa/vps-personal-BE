package io.github.faizul.File.share.GoogleDrive;

import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.File.share.ShareService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class GoogleDriveShareServiceImp implements ShareService {
    @Override
    public Mono<Void> shareFile(UUID fileId, String targetEmail) {
        return null;
    }

    @Override
    public Mono<Void> unshareFile(UUID fileId, Long targetUserId) {
        return null;
    }

    @Override
    public Flux<FileResponse> getSharedWithMe() {
        return null;
    }

    @Override
    public Mono<Boolean> hasReadAccess(UUID fileId, Long userId) {
        return null;
    }
}
