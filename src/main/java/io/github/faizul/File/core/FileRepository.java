package io.github.faizul.File.core;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FileRepository extends ReactiveCrudRepository<File, UUID> {
    Mono<File> findById(UUID id);
    Flux<File> findByUserId(Long userId);

    @Query("SELECT COALESCE(SUM(size), 0) FROM files WHERE user_id = :userId AND (provider = 'STORAGE_NODE' OR provider IS NULL)")
    Mono<Long> calculateUsedStorageByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM files WHERE user_id = :userId AND provider = :provider")
    Mono<Void> deleteByUserIdAndProvider(Long userId, String provider);
}
