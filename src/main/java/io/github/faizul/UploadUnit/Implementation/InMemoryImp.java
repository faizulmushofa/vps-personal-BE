package io.github.faizul.UploadUnit.Implementation;

import io.github.faizul.UploadUnit.UploadUnitTracker;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryImp implements UploadUnitTracker {

    private final ConcurrentHashMap<UUID, Set<Integer>> received = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<Integer>> claimedBatches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> claimedCompletions = new ConcurrentHashMap<>();

    @Override
    public void markReceived(UUID fileId, int index) {
        received.computeIfAbsent(fileId, k -> ConcurrentHashMap.newKeySet()).add(index);
    }

    @Override
    public boolean isReceived(UUID fileId, int index) {
        return received.getOrDefault(fileId, Set.of()).contains(index);
    }

    @Override
    public int countReceived(UUID fileId) {
        return received.getOrDefault(fileId, Set.of()).size();
    }

    @Override
    public boolean isComplete(UUID fileId, int totalChunks) {
        return countReceived(fileId) == totalChunks;
    }

    @Override
    public boolean claimBatch(UUID fileId, int batchIndex) {
        Set<Integer> batches = claimedBatches.computeIfAbsent(fileId, k -> ConcurrentHashMap.newKeySet());
        return batches.add(batchIndex); // Akan false jika sudah ada thread lain yang claim
    }

    @Override
    public boolean claimCompletion(UUID fileId) {
        return claimedCompletions.putIfAbsent(fileId, true) == null; // Akan false jika thread lain sudah claim
    }

    @Override
    public boolean isBatchReady(UUID fileId, int startChunk, int endChunk) {
        Set<Integer> chunks = received.get(fileId);
        if (chunks == null) return false;
        for (int i = startChunk; i <= endChunk; i++) {
            if (!chunks.contains(i)) return false;
        }
        return true;
    }

    @Override
    public void cleanup(UUID fileId) {
        received.remove(fileId);
        claimedBatches.remove(fileId);
        claimedCompletions.remove(fileId);
    }
}
