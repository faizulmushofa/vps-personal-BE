package io.github.faizul.storage.upload.repository;


import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;
import io.github.faizul.storage.upload.model.UploadSession;

public interface UploadSessionRepository extends ReactiveCrudRepository<UploadSession, UUID> {
    Mono<UploadSession> findByFileId(UUID fileId);
}
