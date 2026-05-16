package io.github.faizul.File.Dtos;

public record InitRequest(
        String fileName,
        Long totalSize
) {
}
