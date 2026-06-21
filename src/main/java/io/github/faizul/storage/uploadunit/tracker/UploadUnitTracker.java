package io.github.faizul.storage.uploadunit.tracker;

import java.util.UUID;

public interface UploadUnitTracker {

    void markReceived(UUID fileId, int index);
    boolean isReceived(UUID fileId, int index);
    int countReceived(UUID fileId);
    boolean isComplete(UUID fileId, int totalChunks);
    boolean claimBatch(UUID fileId, int batchIndex);
    boolean claimCompletion(UUID fileId);
    boolean isBatchReady(UUID fileId, int startChunk, int endChunk);
    void cleanup(UUID fileId);
    
}
