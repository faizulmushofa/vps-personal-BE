package io.github.faizul.Infra.Config;

import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Configuration
public class StorageConfig {

    private static final String MODULE_DIR = "vps-personal-backend";

    public Path dataRoot() {
        Path workingDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (isModuleDir(workingDir)) {
            return workingDir.resolve("Data");
        }

        Path moduleDir = workingDir.resolve(MODULE_DIR);
        if (Files.isDirectory(moduleDir)) {
            return moduleDir.resolve("Data");
        }

        return workingDir.resolve("Data");
    }

    public Path tempRoot() {
        return dataRoot().resolve("temp");
    }

    public Path tempDir(UUID fileId) {
        return tempRoot().resolve(fileId.toString());
    }

    private boolean isModuleDir(Path path) {
        Path fileName = path.getFileName();
        return fileName != null && MODULE_DIR.equals(fileName.toString());
    }
}
