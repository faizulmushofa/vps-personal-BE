package io.github.faizul.ai.service.cache;

import io.github.faizul.ai.model.Summary;
import io.github.faizul.ai.repository.SummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SummaryCacheService {

    private final SummaryRepository summaryRepository;

    public Mono<String> getCachedSummary(UUID fileId) {
        return summaryRepository.findByFileId(fileId)
                .map(Summary::getSummary);
    }

    public Mono<String> cacheSummary(UUID fileId, String summaryText) {
        return summaryRepository.findByFileId(fileId)
                .flatMap(existingSummary -> {
                    existingSummary.setSummary(summaryText);
                    return summaryRepository.save(existingSummary);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    Summary newSummary = Summary.builder()
                            .fileId(fileId)
                            .summary(summaryText)
                            .build();
                    return summaryRepository.save(newSummary);
                }))
                .map(Summary::getSummary)
                .onErrorResume(DuplicateKeyException.class, e -> {
                    log.warn("Summary terdeteksi duplikat untuk fileId: {} (mungkin karena request konkuren). Mengambil dari database...", fileId);
                    return getCachedSummary(fileId);
                });
    }
}
