package io.github.faizul.Ai;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiTokenLogRepository extends ReactiveCrudRepository<AiTokenLog, Long> {
}
