package io.github.faizul.File.upload;

import io.github.faizul.File.core.*;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UploadSessionRepository extends ReactiveCrudRepository<UploadSession, UUID> {
    Mono<UploadSession> findByFileId(UUID fileId);
}
