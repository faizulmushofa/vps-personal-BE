package io.github.faizul.folder.core;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FolderRepository extends ReactiveCrudRepository<Folder, UUID> {
    Mono<Folder> findByIdAndUserId(UUID id, Long userId);
    Flux<Folder> findByUserId(Long userId);
    Flux<Folder> findByUserIdAndParentId(Long userId, UUID parentId);
    Flux<Folder> findByUserIdAndParentIdIsNull(Long userId);
    Mono<Boolean> existsByIdAndUserId(UUID id, Long userId);
}
