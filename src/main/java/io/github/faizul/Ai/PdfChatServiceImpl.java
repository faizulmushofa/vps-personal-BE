package io.github.faizul.Ai;

import io.github.faizul.Ai.dtos.AiRequest;
import io.github.faizul.Ai.dtos.AiResponse;
import io.github.faizul.Ai.fallback.AiFallbackService;
import io.github.faizul.File.pdf.PdfService;
import io.github.faizul.infra.config.AiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfChatServiceImpl implements PdfChatService {

    private final AiFallbackService aiFallbackService;
    private final PdfService pdfService;
    private final Scheduler aiScheduler;

    @Override
    public Mono<AiResponse> chatPdf(UUID fileId, AiRequest request) {
        return pdfService.extractFile(fileId)
                .flatMap(text -> {
                    String systemPrompt = "Anda adalah asisten AI yang menjawab pertanyaan pengguna berdasarkan dokumen PDF berikut. " +
                            "Jawablah dengan sopan dan informatif berdasarkan isi dokumen ini:\n\n" + text;
                    return aiFallbackService.callWithFallback(
                            AiConfig.CHAT_PRIMARY_PROVIDER, AiConfig.CHAT_PRIMARY_MODEL,
                            AiConfig.CHAT_FALLBACK_PROVIDER, AiConfig.CHAT_FALLBACK_MODEL,
                            systemPrompt, request.teks()
                    );
                })
                .subscribeOn(aiScheduler)
                .map(AiResponse::new)
                .onErrorResume(Exception.class, e -> {
                    log.error("Gagal melakukan chat PDF untuk fileId: {}", fileId, e);
                    return Mono.just(new AiResponse("Gagal melakukan chat PDF karena masalah teknis."));
                });
    }
}
