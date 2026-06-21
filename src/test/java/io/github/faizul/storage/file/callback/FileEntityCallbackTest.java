package io.github.faizul.storage.file.callback;

import io.github.faizul.security.jwt.EncryptionService;
import io.github.faizul.storage.file.model.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.r2dbc.mapping.OutboundRow;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.r2dbc.core.Parameter;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class FileEntityCallbackTest {

    private EncryptionService encryptionService;
    private FileEntityCallback fileEntityCallback;

    @BeforeEach
    void setUp() {
        encryptionService = Mockito.mock(EncryptionService.class);
        fileEntityCallback = new FileEntityCallback(encryptionService);
    }

    @Test
    @DisplayName("should encrypt original file name and populate OutboundRow when provider is GOOGLE_DRIVE")
    void testBeforeSaveGoogleDrive() {
        File file = File.builder()
                .id(UUID.randomUUID())
                .provider("GOOGLE_DRIVE")
                .originalFileName("academic-paper.pdf")
                .build();

        OutboundRow row = new OutboundRow();
        SqlIdentifier table = SqlIdentifier.unquoted("files");

        when(encryptionService.encrypt("academic-paper.pdf")).thenReturn("encrypted-paper-123");

        StepVerifier.create(Mono.from(fileEntityCallback.onBeforeSave(file, row, table)))
                .assertNext(savedFile -> {
                    assertThat(savedFile.getOriginalFileName()).isEqualTo("encrypted-paper-123");
                    Parameter param = row.get(SqlIdentifier.unquoted("original_file_name"));
                    assertThat(param).isNotNull();
                    assertThat(param.getValue()).isEqualTo("encrypted-paper-123");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("should NOT encrypt when provider is NOT GOOGLE_DRIVE")
    void testBeforeSaveOtherProvider() {
        File file = File.builder()
                .id(UUID.randomUUID())
                .provider("LOCAL")
                .originalFileName("local-file.txt")
                .build();

        OutboundRow row = new OutboundRow();
        SqlIdentifier table = SqlIdentifier.unquoted("files");

        StepVerifier.create(Mono.from(fileEntityCallback.onBeforeSave(file, row, table)))
                .assertNext(savedFile -> {
                    assertThat(savedFile.getOriginalFileName()).isEqualTo("local-file.txt");
                    assertThat(row.get(SqlIdentifier.unquoted("original_file_name"))).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("should decrypt original file name on after convert when provider is GOOGLE_DRIVE")
    void testAfterConvertGoogleDrive() {
        File file = File.builder()
                .id(UUID.randomUUID())
                .provider("GOOGLE_DRIVE")
                .originalFileName("encrypted-paper-123")
                .build();

        SqlIdentifier table = SqlIdentifier.unquoted("files");

        when(encryptionService.decrypt("encrypted-paper-123")).thenReturn("academic-paper.pdf");

        StepVerifier.create(Mono.from(fileEntityCallback.onAfterConvert(file, table)))
                .assertNext(convertedFile -> {
                    assertThat(convertedFile.getOriginalFileName()).isEqualTo("academic-paper.pdf");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("should NOT decrypt when provider is NOT GOOGLE_DRIVE")
    void testAfterConvertOtherProvider() {
        File file = File.builder()
                .id(UUID.randomUUID())
                .provider("LOCAL")
                .originalFileName("local-file.txt")
                .build();

        SqlIdentifier table = SqlIdentifier.unquoted("files");

        StepVerifier.create(Mono.from(fileEntityCallback.onAfterConvert(file, table)))
                .assertNext(convertedFile -> {
                    assertThat(convertedFile.getOriginalFileName()).isEqualTo("local-file.txt");
                })
                .verifyComplete();
    }
}
