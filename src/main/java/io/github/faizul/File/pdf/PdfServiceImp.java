package io.github.faizul.File.pdf;

import io.github.faizul.File.core.FileRepository;
import io.github.faizul.File.core.googleDrive.GoogleDriveClient;
import io.github.faizul.File.download.DownloadService;
import io.github.faizul.Storage.download.DownloadStorageService;
import io.github.faizul.security.filter.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;


import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfServiceImp implements PdfService {

    private final DownloadStorageService downloadStorageService;
    private final CurrentUserContext currentUserContext;
    private final Scheduler pdfScheduler;
    private final FileRepository fileRepository;
    private final GoogleDriveClient googleDriveClient;

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
                                                    log.info("Mendownload dari Google Drive....");
                                                    dataStream = googleDriveClient.downloadFile(userId, file.getStorageName());
                                                } else {
                                                    dataStream = downloadStorageService
                                                            .downloadFile(userId, fileId)
                                                            .map(chunk -> chunk.data());
                                                }

                                                return dataStream
                                                        .publishOn(pdfScheduler)
                                                        .doOnNext(chunk -> {
                                                            log.info("Menulis File Ke disk.....");
                                                            try {
                                                                os.write(chunk);
                                                            } catch (IOException e) {
                                                                throw new RuntimeException(e);
                                                            }
                                                        })
                                                        .then(Mono.fromRunnable(() -> {
                                                            try {
                                                                os.close();
                                                                log.info("File Berhasil Di Download...");
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
                                            .flatMap(this::extract)
                                            .doFinally(signal -> {
                                                try {
                                                    log.info("Menghapus File.. : " + fileId.toString());
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
                log.info(stripper.getText(document));
                return stripper.getText(document);
            }

        }).subscribeOn(pdfScheduler);
    }
}
