package io.github.faizul.File.share;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FileSharedRepository extends R2dbcRepository<FileShared, Long> {
    Mono<Boolean> existsByFileIdAndUserId(UUID fileId, Long userId);
    Mono<Void> deleteByFileIdAndUserId(UUID fileId, Long userId);
    Flux<FileShared> findByUserId(Long userId);
    Mono<FileShared> findByFileIdAndUserId(UUID fileId, Long userId);
    Mono<FileShared> findByShareToken(String shareToken);
    Flux<FileShared> findByFileId(UUID fileId);
}
