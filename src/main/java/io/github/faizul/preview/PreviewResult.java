package io.github.faizul.preview;

import reactor.core.publisher.Flux;

/**
 * Record DTO ringan untuk hasil preview.
 * Tidak memiliki dependensi ke lapisan Repository manapun.
 */
public record PreviewResult(
        String fileName,
        Long size,
        String contentType,
        Flux<byte[]> dataStream
) {}
