package io.github.faizul.extraction.repository;

import io.github.faizul.extraction.model.FileExtraction;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FileExtractionRepository extends R2dbcRepository<FileExtraction, UUID> {
    Mono<FileExtraction> findByFileId(UUID fileId);
}
