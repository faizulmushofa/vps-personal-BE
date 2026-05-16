package io.github.faizul.UploadUnit;

import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UploadUnitWriter {
    Mono<Void> write(UUID fileId, int index, FilePart part);
}
