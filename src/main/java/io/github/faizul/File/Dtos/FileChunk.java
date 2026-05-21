package io.github.faizul.File.Dtos;

public record FileChunk(
    String fileId,
    int chunkIndex,
    int totalChunks,
    byte[] data
) {}
