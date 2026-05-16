package io.github.faizul.File.FileService;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FileRepository extends ReactiveCrudRepository<File,Long> {
    Mono<File> findById(UUID id);
    Flux<File> findByUserId(Long userId);
}
