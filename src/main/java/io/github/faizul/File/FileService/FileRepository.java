package io.github.faizul.File.FileService;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FileRepository extends ReactiveCrudRepository<File, UUID> {
    Mono<File> findById(UUID id);
    Flux<File> findByUserId(Long userId);

    @org.springframework.data.r2dbc.repository.Query("SELECT COALESCE(SUM(size), 0) FROM files WHERE user_id = :userId")
    Mono<Long> calculateUsedStorageByUserId(Long userId);
}
