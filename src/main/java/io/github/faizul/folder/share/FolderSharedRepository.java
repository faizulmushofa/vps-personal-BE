package io.github.faizul.folder.share;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FolderSharedRepository extends ReactiveCrudRepository<FolderShared, Long> {
    Mono<FolderShared> findByShareToken(String shareToken);
    Mono<FolderShared> findByFolderIdAndUserId(String folderId, Long userId);
    Flux<FolderShared> findByUserId(Long userId);
    Mono<Void> deleteByShareToken(String shareToken);
}
