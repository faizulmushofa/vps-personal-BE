package io.github.faizul.storage.uploadunit.helper;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class UploadFileSystem {
    public Path prepare(Path dir, Path target) {
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            return target;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
