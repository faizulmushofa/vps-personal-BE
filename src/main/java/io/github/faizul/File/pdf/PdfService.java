package io.github.faizul.File.pdf;


import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PdfService {

    Mono<String> extractFile(UUID fileId);
}
