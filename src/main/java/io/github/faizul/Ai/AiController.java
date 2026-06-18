package io.github.faizul.Ai;

import io.github.faizul.Ai.dtos.AiRequest;
import io.github.faizul.Ai.dtos.AiResponse;
import io.github.faizul.File.core.FileRepository;
import io.github.faizul.File.core.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/ai")
@Slf4j
public class AiController {

    private final AiService aiService;
    private final PdfChatService pdfChatService;
    private final io.github.faizul.security.filter.CurrentUserContext currentUserContext;
    private final io.github.faizul.activity.UserActivityService userActivityService;
    private final FileRepository fileRepository;
    private final io.github.faizul.User.externalAccount.ExternalAccountRepository externalAccountRepository;
    private final io.github.faizul.File.core.googleDrive.GoogleDriveClient googleDriveClient;

    private Mono<UUID> resolveFileId(String fileIdString, Long userId) {
        try {
            return Mono.just(UUID.fromString(fileIdString));
        } catch (IllegalArgumentException e) {
            return fileRepository.findByStorageNameAndProvider(fileIdString, "GOOGLE_DRIVE")
                    .map(File::getId)
                    .switchIfEmpty(Mono.defer(() -> {
                        log.info("Berkas Google Drive {} tidak ditemukan di database lokal. Memicu JIT import...", fileIdString);
                        return externalAccountRepository.findByUserIdAndProvider(userId, "GOOGLE")
                                .flatMap(account -> googleDriveClient.getFileMetadata(account.getId(), fileIdString)
                                        .flatMap(metadata -> {
                                            String name = (String) metadata.get("name");
                                            long size = 0L;
                                            if (metadata.get("size") != null) {
                                                try {
                                                    size = Long.parseLong(metadata.get("size").toString());
                                                } catch (NumberFormatException ignored) {}
                                            }

                                            log.info("Metadata Google Drive berhasil didapatkan untuk berkas: {}. Menyimpan ke database lokal...", name);
                                            File newFile = File.builder()
                                                    .id(UUID.randomUUID())
                                                    .userId(userId)
                                                    .originalFileName(name)
                                                    .storageName(fileIdString)
                                                    .size(size)
                                                    .provider("GOOGLE_DRIVE")
                                                    .externalAccountId(account.getId())
                                                    .build();
                                            return fileRepository.save(newFile)
                                                    .map(File::getId);
                                        })
                                )
                                .switchIfEmpty(Mono.error(new java.util.NoSuchElementException("Berkas tidak ditemukan!")));
                    }));
        }
    }

    @PostMapping("/summary")
    public Mono<ResponseEntity<AiResponse>> postString(@RequestBody AiRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> aiService.summary(request)
                        .flatMap(response -> userActivityService.log(userId, "AI_SUMMARY", "Melakukan rangkuman teks bebas", exchange)
                                .thenReturn(ResponseEntity.ok().body(response))
                        )
                );
    }

    @PostMapping("/summary/pdf/{fileId}")
    public Mono<ResponseEntity<AiResponse>> summarizePdf(@PathVariable String fileId, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> resolveFileId(fileId, userId)
                        .flatMap(uuid -> aiService.summarizePdf(uuid)
                                .flatMap(response -> userActivityService.log(userId, "AI_SUMMARY_PDF", "Melakukan rangkuman berkas PDF ID: " + fileId, exchange)
                                        .thenReturn(ResponseEntity.ok().body(response))
                                )
                        )
                );
    }

    @PostMapping("/chat/pdf/{fileId}")
    public Mono<ResponseEntity<AiResponse>> chatPdf(@PathVariable String fileId, @RequestBody AiRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> resolveFileId(fileId, userId)
                        .flatMap(uuid -> pdfChatService.chatPdf(uuid, request)
                                .flatMap(response -> userActivityService.log(userId, "AI_CHAT_PDF", "Melakukan tanya-jawab asisten AI pada berkas PDF ID: " + fileId, exchange)
                                        .thenReturn(ResponseEntity.ok().body(response))
                                )
                        )
                );
    }
}
