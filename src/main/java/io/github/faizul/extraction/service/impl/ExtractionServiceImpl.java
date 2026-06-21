package io.github.faizul.extraction.service.impl;

import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.extraction.llamaparse.LlamaParseClient;
import io.github.faizul.extraction.service.ExtractionService;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.storage.download.service.DownloadStorageService;
import io.github.faizul.storage.file.model.File;
import io.github.faizul.storage.file.repository.FileRepository;
import io.github.faizul.storage.file.service.client.GoogleDriveClient;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;


@Service
@RequiredArgsConstructor
@Slf4j
public class ExtractionServiceImpl implements ExtractionService {

    private final DownloadStorageService downloadStorageService;
    private final CurrentUserContext currentUserContext;
    private final Scheduler pdfScheduler;
    private final FileRepository fileRepository;
    private final GoogleDriveClient googleDriveClient;
    private final LlamaParseClient llamaParseClient;
    private final UserActivityService userActivityService;

    @Override
    public Mono<String> extractFile(UUID fileId) {
        log.info("Menjalankan Extraction File : " + fileId.toString());
        return currentUserContext.getUserId()
                .flatMap(userId -> fileRepository.findById(fileId)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("File Not Found")))
                        .flatMap(file -> {
                            Mono<Path> tempFileMono =
                                    Mono.fromCallable(() ->
                                             Files.createTempFile(
                                                     "ocr-",
                                                     ".pdf"
                                             )
                                    );

                            return tempFileMono.flatMap(tempFile ->
                                    Mono.fromCallable(() -> Files.newOutputStream(tempFile))
                                            .flatMap(os -> {
                                                Flux<byte[]> dataStream;
                                                if ("GOOGLE_DRIVE".equals(file.getProvider())) {
                                                    log.info("Mulai mengunduh file dari Google Drive using account: " + file.getExternalAccountId());
                                                    dataStream = googleDriveClient.downloadFile(file.getExternalAccountId(), file.getStorageName());
                                                } else {
                                                    log.info("Mulai mengunduh file dari Storage Node using owner ID: " + file.getUserId());
                                                    dataStream = downloadStorageService
                                                            .downloadFile(file.getUserId(), fileId)
                                                            .map(chunk -> chunk.data());
                                                }

                                                return dataStream
                                                        .publishOn(pdfScheduler)
                                                        .doFirst(() -> log.info("Mulai menulis data file ke disk..."))
                                                        .doOnNext(chunk -> {
                                                            try {
                                                                os.write(chunk);
                                                            } catch (IOException e) {
                                                                throw new RuntimeException(e);
                                                            }
                                                        })
                                                        .doOnComplete(() -> log.info("Semua chunk berhasil diterima untuk fileId: {}", fileId))
                                                        .doOnError(err -> log.error("Error saat download stream fileId: {}", fileId, err))
                                                        .doOnCancel(() -> log.warn("Download stream DIBATALKAN untuk fileId: {}", fileId))
                                                        .then(Mono.fromRunnable(() -> {
                                                            try {
                                                                os.close();
                                                                log.info("File Berhasil Di Download dan ditulis ke disk.");
                                                            } catch (IOException e) {
                                                                throw new RuntimeException(e);
                                                            }
                                                        }))
                                                        .thenReturn(tempFile)
                                                        .doOnError(err -> {
                                                            try {
                                                                os.close();
                                                            } catch (IOException ignored) {}
                                                        });
                                            })
                                            .subscribeOn(pdfScheduler)
                                            .flatMap(path -> {
                                                if (llamaParseClient.isEnabled()) {
                                                    log.info("Mulai ekstraksi PDF menggunakan LlamaParse...");
                                                    return llamaParseClient.parsePdf(path)
                                                            .flatMap(text -> userActivityService.log(userId, "AI_PDF_EXTRACTION", "Mengekstrak teks PDF dengan LlamaParse", null)
                                                                    .thenReturn(text))
                                                            .onErrorResume(err -> {
                                                                log.error("LlamaParse gagal, menggunakan fallback PDFBox. Error: {}", err.getMessage());
                                                                return userActivityService.log(userId, "AI_PDF_EXTRACTION", "Mengekstrak teks PDF dengan PDFBox (Fallback)", null)
                                                                        .then(this.extract(path));
                                                            });
                                                } else {
                                                    log.info("LlamaParse dinonaktifkan, mengekstrak dengan PDFBox...");
                                                    return userActivityService.log(userId, "AI_PDF_EXTRACTION", "Mengekstrak teks PDF dengan PDFBox", null)
                                                            .then(this.extract(path));
                                                }
                                            })
                                            .doFinally(signal -> {
                                                log.info("doFinally signal: {} — Menghapus file untuk fileId: {}", signal, fileId);
                                                try {
                                                    Files.deleteIfExists(tempFile);
                                                } catch (IOException ignored) {
                                                }
                                            })
                            );
                        })
                );
    }


    private Mono<String> extract(Path file) {
        return Mono.fromCallable( () -> {

            try(PDDocument document = Loader.loadPDF(file.toFile())) {

                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                log.info("Ekstraksi PDF berhasil: {} karakter", text != null ? text.length() : 0);
                return text;
            }

        }).subscribeOn(pdfScheduler);
    }
}
