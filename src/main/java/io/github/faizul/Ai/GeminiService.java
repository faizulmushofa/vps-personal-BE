package io.github.faizul.Ai;

import io.github.faizul.Ai.dtos.AiRequest;
import io.github.faizul.Ai.dtos.AiResponse;
import io.github.faizul.File.pdf.PdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import org.springframework.dao.DuplicateKeyException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService implements AiService {

    private final ChatClient chatClient;
    private final Scheduler aiScheduler;
    private final PdfService pdfService;
    private final SummaryRepository summaryRepository;

    @Override
    public Mono<AiResponse> summary(AiRequest request) {
        return Mono.fromCallable(() -> chatClient.prompt()
                .user(request.teks())
                .call()
                .content())
                .subscribeOn(aiScheduler)
                .map(AiResponse::new)
                .onErrorResume(Exception.class, e -> {
                    log.error("Gagal memanggil Gemini API untuk request: {}", request, e);
                    return Mono.just(new AiResponse("Gagal menghasilkan ringkasan karena masalah teknis."));
                });
    }

    @Override
    public Mono<AiResponse> summarizePdf(UUID fileId) {
        log.info("Memanggil Function PDF summary untuk fileId: {}", fileId);
        return summaryRepository.findByFileId(fileId)
                .map(summary -> {
                    log.info("Summary ditemukan di database untuk fileId: {}. Konten summary: {}", fileId,
                            summary.getSummary());
                    return new AiResponse(summary.getSummary());
                })
                .switchIfEmpty(
                        Mono.defer(() -> {
                            log.info(
                                    "Summary tidak ditemukan di database. Melakukan ekstraksi PDF dan pemanggilan AI...");
                            return pdfService.extractFile(fileId)
                                    .map((text) -> {
                                        log.info(
                                                "Berhasil mengekstrak teks dari PDF fileId: {}. Panjang teks: {} karakter.",
                                                fileId, text.length());
                                        return new AiRequest(
                                                "Tolong rangkum teks berikut secara singkat dan jelas dalam Bahasa Indonesia:\n\n"
                                                        + text);
                                    })
                                    .flatMap(this::summary)
                                    .flatMap(aiResponse -> {
                                        if ("Gagal menghasilkan ringkasan karena masalah teknis."
                                                .equals(aiResponse.response())) {
                                            return Mono.just(aiResponse);
                                        }
                                        Summary newSummary = Summary.builder()
                                                .fileId(fileId)
                                                .summary(aiResponse.response())
                                                .build();
                                        return summaryRepository.save(newSummary)
                                                .doOnSuccess(saved -> log.info(
                                                        "Berhasil menyimpan summary baru ke database untuk fileId: {}",
                                                        fileId))
                                                .thenReturn(aiResponse)
                                                .onErrorResume(DuplicateKeyException.class, e -> {
                                                    log.warn(
                                                            "Summary terdeteksi duplikat untuk fileId: {} (mungkin karena request konkuren). Mengambil dari database...",
                                                            fileId);
                                                    return summaryRepository.findByFileId(fileId)
                                                            .map(existingSummary -> new AiResponse(
                                                                    existingSummary.getSummary()));
                                                });
                                    });
                        }))
                .onErrorResume(Exception.class, e -> {
                    log.error("Gagal melakukan ringkasan PDF untuk fileId: {}", fileId, e);
                    return Mono.just(new AiResponse("Gagal menghasilkan ringkasan PDF karena masalah teknis."));
                });
    }
}
