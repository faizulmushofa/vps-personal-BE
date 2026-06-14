package io.github.faizul.migration;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface MigrationTaskRepository extends ReactiveCrudRepository<MigrationTask, UUID> {
    
    Flux<MigrationTask> findByBatchId(UUID batchId);
    
    Flux<MigrationTask> findByUserId(Long userId);
    
    Flux<MigrationTask> findByUserIdAndStatus(Long userId, MigrationStatus status);
    
    @Query("SELECT COUNT(*) FROM migration_tasks WHERE user_id = :userId AND created_at >= :startOfDay")
    Mono<Long> countByUserIdAndCreatedAtAfter(Long userId, Instant startOfDay);

    Mono<Boolean> existsByUserIdAndStatus(Long userId, MigrationStatus status);
}
