package io.github.faizul.infra.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class EnvLoader {

    public static String get(String key) {
        // 1. Coba baca dari System Environment (OS)
        String value = System.getenv(key);
        if (value != null && !value.trim().isEmpty()) {
            return value;
        }

        // 2. Coba baca dari System Properties
        value = System.getProperty(key);
        if (value != null && !value.trim().isEmpty()) {
            return value;
        }

        // 3. Coba baca secara manual dari berkas .env
        try {
            Path envPath = Paths.get(".env");
            if (Files.exists(envPath)) {
                List<String> lines = Files.readAllLines(envPath);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    if (trimmed.startsWith(key + "=")) {
                        return trimmed.substring((key + "=").length()).trim();
                    }
                }
            }
        } catch (IOException e) {
            // Abaikan kesalahan
        }

        return null;
    }

    public static String getOrDefault(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }
}
