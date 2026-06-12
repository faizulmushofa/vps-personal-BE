package io.github.faizul.preview;

import io.github.faizul.File.core.FileService;
import io.github.faizul.File.download.DownloadService;
import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.File.share.ShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PreviewServiceImplTest {

    @Mock private FileService fileService;
    @Mock private DownloadService storageNodeDownloadService;
    @Mock private DownloadService googleDriveDownloadService;
    @Mock private ShareService storageNodeShareService;
    @Mock private ShareService googleDriveShareService;

    private PreviewServiceImpl previewService;

    @BeforeEach
    void setUp() {
        previewService = new PreviewServiceImpl(
                fileService,
                storageNodeDownloadService,
                googleDriveDownloadService,
                storageNodeShareService,
                googleDriveShareService
        );
    }

    @Nested
    @DisplayName("previewPrivateFile")
    class PreviewPrivateFileTests {

        @Test
        @DisplayName("should preview storage node file with correct content type")
        void previewPrivateFile_storageNode() {
            UUID fileId = UUID.randomUUID();
            FileResponse fileResponse = new FileResponse(
                    fileId, "document.pdf", 1024L, Instant.now(),
                    "STORAGE_NODE", null, "user@example.com");

            when(fileService.findByUUID(fileId)).thenReturn(Mono.just(fileResponse));
            when(storageNodeDownloadService.streamFile(fileId))
                    .thenReturn(Flux.just(new byte[]{1, 2, 3}));

            StepVerifier.create(previewService.previewPrivateFile(fileId))
                    .assertNext(result -> {
                        assertThat(result.fileName()).isEqualTo("document.pdf");
                        assertThat(result.size()).isEqualTo(1024L);
                        assertThat(result.contentType()).isEqualTo("application/pdf");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should preview Google Drive file")
        void previewPrivateFile_googleDrive() {
            UUID fileId = UUID.randomUUID();
            FileResponse fileResponse = new FileResponse(
                    fileId, "image.png", 2048L, Instant.now(),
                    "GOOGLE_DRIVE", 1L, "user@example.com");

            when(fileService.findByUUID(fileId)).thenReturn(Mono.just(fileResponse));
            when(googleDriveDownloadService.streamFile(fileId))
                    .thenReturn(Flux.just(new byte[]{1, 2, 3}));

            StepVerifier.create(previewService.previewPrivateFile(fileId))
                    .assertNext(result -> {
                        assertThat(result.fileName()).isEqualTo("image.png");
                        assertThat(result.contentType()).isEqualTo("image/png");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should return empty when file not found")
        void previewPrivateFile_notFound() {
            UUID fileId = UUID.randomUUID();
            when(fileService.findByUUID(fileId)).thenReturn(Mono.empty());

            StepVerifier.create(previewService.previewPrivateFile(fileId))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("previewPublicFile")
    class PreviewPublicFileTests {

        @Test
        @DisplayName("should preview public local file")
        void previewPublicFile_local() {
            FileResponse fileResponse = new FileResponse(
                    UUID.randomUUID(), "report.pdf", 5000L, Instant.now(),
                    "STORAGE_NODE", null, "owner@example.com");

            when(storageNodeShareService.getPublicFileInfo("share-token-123"))
                    .thenReturn(Mono.just(fileResponse));
            when(storageNodeShareService.downloadPublicFile("share-token-123"))
                    .thenReturn(Flux.just(new byte[]{1, 2, 3}));

            StepVerifier.create(previewService.previewPublicFile("share-token-123", "local"))
                    .assertNext(result -> {
                        assertThat(result.fileName()).isEqualTo("report.pdf");
                        assertThat(result.contentType()).isEqualTo("application/pdf");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should preview public Google Drive file")
        void previewPublicFile_google() {
            FileResponse fileResponse = new FileResponse(
                    UUID.randomUUID(), "video.mp4", 10000L, Instant.now(),
                    "GOOGLE_DRIVE", 1L, "owner@example.com");

            when(googleDriveShareService.getPublicFileInfo("share-token-456"))
                    .thenReturn(Mono.just(fileResponse));
            when(googleDriveShareService.downloadPublicFile("share-token-456"))
                    .thenReturn(Flux.just(new byte[]{1, 2, 3}));

            StepVerifier.create(previewService.previewPublicFile("share-token-456", "google"))
                    .assertNext(result -> {
                        assertThat(result.fileName()).isEqualTo("video.mp4");
                        assertThat(result.contentType()).isEqualTo("video/mp4");
                    })
                    .verifyComplete();
        }
    }
}
