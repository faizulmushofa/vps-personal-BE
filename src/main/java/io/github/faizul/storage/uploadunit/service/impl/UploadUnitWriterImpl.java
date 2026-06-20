package io.github.faizul.storage.uploadunit.service.impl;

import io.github.faizul.infra.config.*;
import io.github.faizul.infra.config.StorageConfig;
import io.github.faizul.security.filter.*;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.storage.uploadunit.helper.UploadFileSystem;
import io.github.faizul.storage.uploadunit.service.UploadUnitWriter;
import java.nio.file.Path;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;




@Component
@RequiredArgsConstructor
public class UploadUnitWriterImpl implements UploadUnitWriter {

    private final Scheduler fileWriteScheduler;
    private final UploadFileSystem fileSystem;
    private final StorageConfig storageConfig;
    private final CurrentUserContext currentUserContext;

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
