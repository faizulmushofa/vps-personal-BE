package io.github.faizul.ai.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;
import io.github.faizul.ai.model.Summary;

public interface SummaryRepository extends ReactiveCrudRepository<Summary, Long> {
    Mono<Summary> findByFileId(UUID fileId);
}
