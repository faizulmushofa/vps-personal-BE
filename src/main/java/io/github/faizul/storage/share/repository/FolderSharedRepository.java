package io.github.faizul.storage.share.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import io.github.faizul.storage.share.model.FolderShared;

public interface FolderSharedRepository extends ReactiveCrudRepository<FolderShared, Long> {
    Mono<FolderShared> findByShareToken(String shareToken);
    Mono<FolderShared> findByFolderIdAndUserId(String folderId, Long userId);
    Flux<FolderShared> findByUserId(Long userId);
    Mono<Void> deleteByShareToken(String shareToken);
}
