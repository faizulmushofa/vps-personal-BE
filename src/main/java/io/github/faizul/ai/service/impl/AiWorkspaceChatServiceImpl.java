package io.github.faizul.ai.service.impl;

import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.ai.dtos.AiRequest;
import io.github.faizul.ai.dtos.AiResponse;
import io.github.faizul.ai.model.AiWorkspaceChat;
import io.github.faizul.ai.model.AiWorkspaceMessage;
import io.github.faizul.ai.repository.*;
import io.github.faizul.ai.service.AiQuotaAndLogService;
import io.github.faizul.ai.service.AiWorkspaceChatService;
import io.github.faizul.ai.service.cache.SummaryCacheService;
import io.github.faizul.ai.service.fallback.AiFallbackService;
import io.github.faizul.infra.config.AiConfig;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.ai.service.AiConfigService;
import io.github.faizul.ai.dtos.AiSettings;
import io.github.faizul.storage.file.repository.FileRepository;
import io.github.faizul.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AiWorkspaceChatServiceImpl implements AiWorkspaceChatService {

    private final AiWorkspaceRepository aiWorkspaceRepository;
    private final AiWorkspaceFileRepository aiWorkspaceFileRepository;
    private final AiWorkspaceNoteRepository aiWorkspaceNoteRepository;
    private final AiWorkspaceChatRepository aiWorkspaceChatRepository;
    private final AiWorkspaceMessageRepository aiWorkspaceMessageRepository;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final CurrentUserContext currentUserContext;
    private final AiFallbackService aiFallbackService;
    private final SummaryCacheService cacheService;
    private final AiConfigService aiConfigService;
    private final AiQuotaAndLogService quotaAndLogService;
    private final UserActivityService userActivityService;
    private final Scheduler aiScheduler;

    @Override
    public Mono<AiWorkspaceChat> getOrCreateActiveChat(UUID workspaceId) {
        return aiWorkspaceChatRepository.findFirstByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .switchIfEmpty(Mono.defer(() -> {
                    AiWorkspaceChat chat = AiWorkspaceChat.builder()
                            .id(UUID.randomUUID())
                            .workspaceId(workspaceId)
                            .title("Sesi Obrolan")
                            .isNewRecord(true)
                            .build();
                    return aiWorkspaceChatRepository.save(chat);
                }));
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<AiWorkspaceChat> getChats(UUID workspaceId) {
        return aiWorkspaceChatRepository.findAllByWorkspaceId(workspaceId);
    }

    @Override
    @Transactional(readOnly = true)
    public Flux<AiWorkspaceMessage> getChatMessages(UUID chatId) {
        return aiWorkspaceMessageRepository.findAllByChatIdOrderByCreatedAtAsc(chatId);
    }

    @Override
    public Mono<AiResponse> chatWorkspace(UUID workspaceId, UUID chatId, AiRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> quotaAndLogService.checkAndIncrementQuota(userId)
                        .flatMap(user -> aiWorkspaceRepository.findByIdAndUserId(workspaceId, userId)
                                .switchIfEmpty(Mono.error(new NoSuchElementException("Ruang kerja tidak ditemukan!")))
                                .flatMap(ws -> buildGroundingContext(workspaceId)
                                        .flatMap(context -> {
                                            // 1. Cek Token Limit
                                            int approxTokens = context.length() / 4;
                                            int maxTokens = user.getSubscriptionPlan().getLimits().maxInputTokensPerRequest();
                                            if (maxTokens != -1 && approxTokens > maxTokens) {
                                                return Mono.error(new IllegalArgumentException(
                                                        "Konteks sumber data terlalu besar (" + approxTokens + " token). Batas maksimal paket Anda adalah " + maxTokens + " token. Silakan nonaktifkan beberapa dokumen di panel kiri."));
                                            }

                                            // 2. Simpan pesan user
                                            AiWorkspaceMessage userMsg = AiWorkspaceMessage.builder()
                                                    .id(UUID.randomUUID())
                                                    .chatId(chatId)
                                                    .role("USER")
                                                    .content(request.teks())
                                                    .isNewRecord(true)
                                                    .build();

                                            return aiWorkspaceMessageRepository.save(userMsg)
                                                    .then(aiConfigService.getChatSettings().flatMap(settings -> {
                                                        String systemPrompt = settings.systemPrompt() + "\n\n=== DOKUMEN SUMBER & CATATAN WORKSPACE ===\n" + context + "\n=== INSTRUKSI ===\nJawab pertanyaan pengguna di atas HANYA berdasarkan dokumen sumber dan catatan yang disediakan di atas. Jangan berasumsi.";

                                                        return aiFallbackService.callWithFallback(
                                                                settings.primaryProvider(), settings.primaryModel(),
                                                                settings.fallbackProvider(), settings.fallbackModel(),
                                                                settings.fallback2Provider(), settings.fallback2Model(),
                                                                systemPrompt, request.teks()
                                                        )
                                                        .flatMap(result -> {
                                                            AiWorkspaceMessage assistantMsg = AiWorkspaceMessage.builder()
                                                                    .id(UUID.randomUUID())
                                                                    .chatId(chatId)
                                                                    .role("ASSISTANT")
                                                                    .content(result.content())
                                                                    .isNewRecord(true)
                                                                    .build();

                                                            return aiWorkspaceMessageRepository.save(assistantMsg)
                                                                    .then(quotaAndLogService.logTokenUsage(userId, "WORKSPACE_CHAT", settings.primaryProvider(), settings.primaryModel(), result)
                                                                            .onErrorResume(err -> Mono.empty())
                                                                    )
                                                                    .then(userActivityService.log(userId, "AI_WORKSPACE_CHAT", "Tanya-jawab asisten AI di ruang kerja ID: " + workspaceId, exchange)
                                                                            .onErrorResume(err -> Mono.empty())
                                                                    )
                                                                    .thenReturn(new AiResponse(result.content()));
                                                        });
                                                    }));
                                        })
                                )
                        )
                )
                .subscribeOn(aiScheduler);
    }

    @Override
    public Mono<AiResponse> generateWorkspaceDoc(UUID workspaceId, String type, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> quotaAndLogService.checkAndIncrementQuota(userId)
                        .flatMap(user -> aiWorkspaceRepository.findByIdAndUserId(workspaceId, userId)
                                .switchIfEmpty(Mono.error(new NoSuchElementException("Ruang kerja tidak ditemukan!")))
                                .flatMap(ws -> buildGroundingContext(workspaceId)
                                        .flatMap(context -> {
                                            // 1. Cek Token Limit
                                            int approxTokens = context.length() / 4;
                                            int maxTokens = user.getSubscriptionPlan().getLimits().maxInputTokensPerRequest();
                                            if (maxTokens != -1 && approxTokens > maxTokens) {
                                                return Mono.error(new IllegalArgumentException(
                                                        "Konteks sumber data terlalu besar (" + approxTokens + " token). Batas maksimal paket Anda adalah " + maxTokens + " token. Silakan nonaktifkan beberapa dokumen di panel kiri."));
                                            }

                                            // 2. Susun prompt berdasarkan type
                                            String instruction;
                                            String activityDesc;
                                            if ("faq".equalsIgnoreCase(type)) {
                                                instruction = "Berdasarkan dokumen sumber dan catatan ruang kerja berikut, buatlah daftar Frequently Asked Questions (FAQ) beserta jawabannya secara lengkap, akurat, dan terperinci. Format output harus dalam Markdown yang rapi.";
                                                activityDesc = "Menghasilkan FAQ otomatis di ruang kerja ID: ";
                                            } else if ("study-guide".equalsIgnoreCase(type)) {
                                                instruction = "Berdasarkan dokumen sumber dan catatan ruang kerja berikut, buatlah Study Guide (Panduan Belajar) yang mencakup istilah-istilah kunci beserta definisinya, ringkasan konsep utama yang terstruktur, dan 5 kuis/pertanyaan pendek beserta kunci jawabannya di bagian akhir. Format output harus dalam Markdown yang rapi.";
                                                activityDesc = "Menghasilkan Study Guide otomatis di ruang kerja ID: ";
                                            } else {
                                                instruction = "Berdasarkan dokumen sumber dan catatan ruang kerja berikut, buatlah Briefing Doc (Ringkasan Eksekutif). Tulis ringkasan singkat, poin-poin utama, dan kesimpulan strategis. Format output harus dalam Markdown yang rapi.";
                                                activityDesc = "Menghasilkan Briefing Doc otomatis di ruang kerja ID: ";
                                            }

                                            return aiConfigService.getChatSettings().flatMap(settings -> {
                                                String systemPrompt = settings.systemPrompt() + "\n\n=== KONTEKS DOKUMEN ===\n" + context;

                                                return aiFallbackService.callWithFallback(
                                                        settings.primaryProvider(), settings.primaryModel(),
                                                        settings.fallbackProvider(), settings.fallbackModel(),
                                                        settings.fallback2Provider(), settings.fallback2Model(),
                                                        systemPrompt, instruction
                                                )
                                                .flatMap(result -> quotaAndLogService.logTokenUsage(userId, "WORKSPACE_GEN_" + type.toUpperCase(), settings.primaryProvider(), settings.primaryModel(), result)
                                                        .onErrorResume(err -> Mono.empty())
                                                        .then(userActivityService.log(userId, "AI_WORKSPACE_GEN_" + type.toUpperCase(), activityDesc + workspaceId, exchange)
                                                                .onErrorResume(err -> Mono.empty())
                                                        )
                                                        .thenReturn(new AiResponse(result.content()))
                                                );
                                            });
                                        })
                                )
                        )
                )
                .subscribeOn(aiScheduler);
    }

    private Mono<String> buildGroundingContext(UUID workspaceId) {
        // Ambil summary file
        Mono<List<String>> filesContextMono = aiWorkspaceFileRepository.findAllByWorkspaceId(workspaceId)
                .flatMap(wsFile -> fileRepository.findById(wsFile.getFileId())
                        .flatMap(file -> cacheService.getCachedSummary(file.getId())
                                .map(summary -> "DOKUMEN: " + file.getOriginalFileName() + "\nRingkasan: " + summary + "\n")
                                .defaultIfEmpty("DOKUMEN: " + file.getOriginalFileName() + "\nRingkasan: Tidak ada ringkasan.\n")
                        )
                )
                .collectList();

        // Ambil catatan pengguna
        Mono<List<String>> notesContextMono = aiWorkspaceNoteRepository.findAllByWorkspaceId(workspaceId)
                .map(note -> "CATATAN: " + note.getTitle() + "\nIsi Catatan: " + note.getContent() + "\n")
                .collectList();

        return Mono.zip(filesContextMono, notesContextMono)
                .map(tuple -> {
                    StringBuilder context = new StringBuilder();
                    context.append("--- BERKAS-BERKAS ---\n");
                    for (String fc : tuple.getT1()) {
                        context.append(fc).append("\n");
                    }
                    context.append("--- CATATAN PRIBADI ---\n");
                    for (String nc : tuple.getT2()) {
                        context.append(nc).append("\n");
                    }
                    return context.toString();
                });
    }
}
