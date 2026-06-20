package io.github.faizul.extraction.service;


import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ExtractionService {
    Mono<String> extractFile(UUID fileId);
}
