package io.github.faizul.UploadUnit;

import java.util.UUID;

public interface UploadUnitTracker {
    void markReceived(UUID fileId, int index);
    boolean isReceived(UUID fileId, int index);
    int countReceived(UUID fileId);
    boolean isComplete(UUID fileId, int totalChunks);
    
    // Menghindari Race Conditions (Atomic Claims)
    boolean claimBatch(UUID fileId, int batchIndex);
    boolean claimCompletion(UUID fileId);
    
    // Out-of-Order Batch Validation
    boolean isBatchReady(UUID fileId, int startChunk, int endChunk);
    
    // Mencegah Out-Of-Memory (OOM)
    void cleanup(UUID fileId);
}
