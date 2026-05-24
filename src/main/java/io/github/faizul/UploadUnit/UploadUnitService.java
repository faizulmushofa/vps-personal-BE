package io.github.faizul.UploadUnit;

import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UploadUnitService {

    Mono<Void> receiveUnit(UUID uuid, int index, FilePart filePart);
    Mono<Boolean> isUnitReceived(UUID fileId,int index);
    Mono<Integer> getReceivedUnit(UUID fileId);
    Mono<Boolean> isComplete(UUID fileId, int totalChunks);
    Mono<Boolean> isBatchReady(UUID fileId, int startChunk,int endChunk);
    Mono<Boolean> claimBatch(UUID fileId, int batchIndex);
    Mono<Boolean> claimCompletion(UUID fileId);
    Mono<Void> cleanupMemory(UUID fileId);

}
