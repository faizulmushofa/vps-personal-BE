package io.github.faizul.UploadUnit.Helper;

public class Chunk {

    private static final long CHUNK_SIZE = 5 * 1024 * 1024; // 5MB

    public static long getChunkSize() {
        return CHUNK_SIZE;
    }

    public static int calculateTotalChunks(long fileSize) {
        return (int) Math.ceil((double) fileSize / CHUNK_SIZE);
    }
}