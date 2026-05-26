package io.github.faizul.File.pdf;

import io.github.faizul.File.download.DownloadService;
import io.github.faizul.Storage.download.DownloadStorageService;
import io.github.faizul.security.filter.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;


import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PdfServiceImp implements PdfService {

    private final DownloadStorageService downloadStorageService;
    private final CurrentUserContext currentUserContext;
    private final Scheduler pdfScheduler;

    @Override
    public Mono<String> extractFile(UUID fileId) {

        return currentUserContext.getUserId()
                .flatMap(userId -> {

                    Mono<Path> tempFileMono =
                            Mono.fromCallable(() ->
                                    Files.createTempFile(
                                            "ocr-",
                                            ".pdf"
                                    )
                            );

                    return tempFileMono.flatMap(tempFile ->

                            Mono.fromCallable(() -> {

                                        try (OutputStream os =
                                                     Files.newOutputStream(tempFile)) {

                                            downloadStorageService
                                                    .downloadFile(userId, fileId)
                                                    .doOnNext(chunk -> {

                                                        try {

                                                            os.write(chunk.data());

                                                        } catch (IOException e) {
                                                            throw new RuntimeException(e);
                                                        }

                                                    })
                                                    .blockLast();
                                        }

                                        return tempFile;

                                    })
                                    .subscribeOn(pdfScheduler)
                                    .flatMap(this::extract)
                                    .doFinally(signal -> {

                                        try {
                                            Files.deleteIfExists(tempFile);

                                        } catch (IOException ignored) {

                                        }
                                    })
                    );
                });
    }


    private Mono<String> extract(Path file) {
        return Mono.fromCallable( () -> {

            try(PDDocument document = Loader.loadPDF(file.toFile())) {

                PDFTextStripper stripper = new PDFTextStripper();

                return stripper.getText(document);
            }

        }).subscribeOn(pdfScheduler);
    }
}
