package io.github.faizul.File.share;

import org.springframework.data.r2dbc.repository.Query;
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

    @Query("SELECT COUNT(*) FROM file_shared fs JOIN files f ON fs.file_id = f.id WHERE f.user_id = :ownerId AND fs.is_public = TRUE AND (fs.expires_at IS NULL OR fs.expires_at > NOW())")
    Mono<Long> countActivePublicSharesByOwnerId(Long ownerId);

    @Query("SELECT COUNT(*) FROM file_shared fs JOIN files f ON fs.file_id = f.id WHERE f.user_id = :ownerId AND fs.is_public = FALSE AND (fs.expires_at IS NULL OR fs.expires_at > NOW())")
    Mono<Long> countActivePrivateSharesByOwnerId(Long ownerId);
}
