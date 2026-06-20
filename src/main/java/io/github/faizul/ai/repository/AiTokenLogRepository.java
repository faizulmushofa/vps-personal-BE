package io.github.faizul.ai.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import io.github.faizul.ai.model.AiTokenLog;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import java.time.LocalDateTime;

@Repository
public interface AiTokenLogRepository extends ReactiveCrudRepository<AiTokenLog, Long> {

    record TokenStatsTuple(Long inputTokens, Long outputTokens, Long totalTokens) {}

    record TokenHistoryTuple(String logDate, Long inputTokens, Long outputTokens, Long totalTokens) {}

    @Query("SELECT COALESCE(SUM(input_tokens), 0) as input_tokens, " +
           "COALESCE(SUM(output_tokens), 0) as output_tokens, " +
           "COALESCE(SUM(total_tokens), 0) as total_tokens " +
           "FROM ai_token_logs WHERE created_at >= :start")
    Mono<TokenStatsTuple> getStatsSince(LocalDateTime start);

    @Query("SELECT CAST(created_at AS DATE) as log_date, " +
           "COALESCE(SUM(input_tokens), 0) as input_tokens, " +
           "COALESCE(SUM(output_tokens), 0) as output_tokens, " +
           "COALESCE(SUM(total_tokens), 0) as total_tokens " +
           "FROM ai_token_logs WHERE created_at >= :start " +
           "GROUP BY CAST(created_at AS DATE) " +
           "ORDER BY log_date ASC")
    Flux<TokenHistoryTuple> getHistorySince(LocalDateTime start);
}
