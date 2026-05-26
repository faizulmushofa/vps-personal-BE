package io.github.faizul.UploadUnit.Implementation;

import io.github.faizul.Infra.Config.StorageConfig;
import io.github.faizul.UploadUnit.Helper.UploadFileSystem;
import io.github.faizul.UploadUnit.UploadUnitWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.nio.file.Path;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UploadUnitWriterImp implements UploadUnitWriter {

    private final Scheduler fileWriteScheduler;
    private final UploadFileSystem fileSystem;
    private final StorageConfig storageConfig;
    private final io.github.faizul.Infra.Security.CurrentUserContext currentUserContext;

    @Override
    public Mono<Void> write(UUID fileId, int index, FilePart part) {

        return currentUserContext.getUserId().flatMap(userId -> {
            Path dir = storageConfig.tempDir(userId, fileId);
            Path target = dir.resolve("chunk-" + index);

            return Mono.fromCallable(() -> fileSystem.prepare(dir, target))
                    .subscribeOn(fileWriteScheduler)
                    .flatMap(part::transferTo)
                    .then();
        });
    }
}
