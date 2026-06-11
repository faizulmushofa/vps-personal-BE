package io.github.faizul.Ai;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SummaryRepository extends ReactiveCrudRepository<Summary, Long> {
    Mono<Summary> findByFileId(UUID fileId);
}
