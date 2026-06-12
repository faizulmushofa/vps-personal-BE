package io.github.faizul.UploadUnit.Implementation;

import io.github.faizul.UploadUnit.Helper.UploadFileSystem;
import io.github.faizul.UploadUnit.UploadUnitWriter;
import io.github.faizul.infra.config.StorageConfig;
import io.github.faizul.security.filter.CurrentUserContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.nio.file.Path;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadUnitWriterTest {

    @Mock private UploadFileSystem fileSystem;
    @Mock private StorageConfig storageConfig;
    @Mock private CurrentUserContext currentUserContext;
    @Mock private FilePart filePart;

    @Test
    @DisplayName("should write chunk file to temp directory")
    void write_success(@TempDir Path tempDir) {
        UUID fileId = UUID.randomUUID();
        Path chunkDir = tempDir.resolve("1").resolve(fileId.toString());
        Path chunkFile = chunkDir.resolve("chunk-0");

        when(currentUserContext.getUserId()).thenReturn(Mono.just(1L));
        when(storageConfig.tempDir(1L, fileId)).thenReturn(chunkDir);
        when(fileSystem.prepare(chunkDir, chunkFile)).thenReturn(chunkFile);
        when(filePart.transferTo(chunkFile)).thenReturn(Mono.empty());

        UploadUnitWriterImp writer = new UploadUnitWriterImp(
                Schedulers.immediate(), fileSystem, storageConfig, currentUserContext);

        StepVerifier.create(writer.write(fileId, 0, filePart))
                .verifyComplete();

        verify(fileSystem).prepare(chunkDir, chunkFile);
        verify(filePart).transferTo(chunkFile);
    }
}
