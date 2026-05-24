package io.github.faizul.Storage.Upload;

import com.google.protobuf.ByteString;
import io.github.faizul.Infra.Config.StorageConfig;
import io.github.storagenode.grpc.upload.FinalizeRequest;
import io.github.storagenode.grpc.upload.UploadBatchResponse;
import io.github.storagenode.grpc.upload.UploadChunkRequest;
import io.github.storagenode.grpc.upload.DeleteFileRequest;
import io.github.storagenode.grpc.upload.DeleteFileResponse;
import io.github.storagenode.grpc.upload.UploadServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class UploadStorageImp implements UploadStorageService {

    // Ukuran aman untuk gRPC: 256 KB per payload
    private static final int GRPC_PAYLOAD_SIZE = 256 * 1024; 
    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();
    private final UploadServiceGrpc.UploadServiceStub uploadStub;
    private final StorageConfig storageConfig;

    public UploadStorageImp(GrpcChannelFactory channels, StorageConfig storageConfig) {
        ManagedChannel channel = channels.createChannel("storage-node");
        this.uploadStub = UploadServiceGrpc.newStub(channel);
        this.storageConfig = storageConfig;
    }

    @Override
    public Mono<Void> sendBatch(Long userId, UUID fileId, int startChunk, int endChunk) {
        Path dir = storageConfig.tempDir(userId, fileId);

        // 1. Looping reaktif dari startChunk ke endChunk
        return Flux.range(startChunk, endChunk - startChunk + 1)
                // 2. Baca tiap file chunk dan pecah menjadi aliran bytes 256KB
                .concatMap(index -> {
                    Path chunkPath = dir.resolve("chunk-" + index);
                    return streamFileInSlices(userId, fileId, chunkPath, index);
                })
                // 3. Pipa (Pipeline) ke Klien gRPC
                .as(this::uploadBatch)
                .then();
    }

    @Override
    public Mono<Void> sendFinalSignal(Long userId, UUID fileId, int totalChunks) {
        log.info("Sending Final Signal to gRPC for file: {}", fileId);
        FinalizeRequest request = FinalizeRequest.newBuilder()
                .setFileId(fileId.toString())
                .setUserId(userId.toString())
                .setTotalChunks(totalChunks)
                .build();

        return finalizeUpload(request)
                .timeout(Duration.ofSeconds(30))
                .flatMap(response -> {
                    if (response.getSuccess()) {
                        return Mono.empty();
                    }
                    return Mono.error(new IllegalStateException(response.getMessage()));
                });
    }

    /**
     * Membaca file dari hardisk dengan "sedotan kecil" (256KB) 
     * secara Non-Blocking.
     */
    private Flux<UploadChunkRequest> streamFileInSlices(Long userId, UUID fileId, Path filePath, int chunkIndex) {
        FileSystemResource resource = new FileSystemResource(filePath);
        
        return DataBufferUtils.read(resource, bufferFactory, GRPC_PAYLOAD_SIZE)
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    
                    // PENTING: Bebaskan memori DataBuffer agar tidak Memory Leak (OOM)
                    DataBufferUtils.release(dataBuffer); 

                    return UploadChunkRequest.newBuilder()
                            .setFileId(fileId.toString())
                            .setUserId(userId.toString())
                            .setChunkIndex(chunkIndex)
                            .setData(ByteString.copyFrom(bytes))
                            .build();
                });
    }

    private Mono<Void> uploadBatch(Flux<UploadChunkRequest> pipeline) {
        return Mono.create(sink -> {
            AtomicBoolean terminated = new AtomicBoolean(false);

            StreamObserver<UploadBatchResponse> responseObserver = new StreamObserver<>() {
                @Override
                public void onNext(UploadBatchResponse response) {
                    if (!response.getSuccess() && terminated.compareAndSet(false, true)) {
                        sink.error(new IllegalStateException(response.getMessage()));
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    if (terminated.compareAndSet(false, true)) {
                        sink.error(throwable);
                    }
                }

                @Override
                public void onCompleted() {
                    if (terminated.compareAndSet(false, true)) {
                        sink.success();
                    }
                }
            };

            StreamObserver<UploadChunkRequest> requestObserver = uploadStub.uploadBatch(responseObserver);

            pipeline.subscribe(
                    requestObserver::onNext,
                    requestObserver::onError,
                    requestObserver::onCompleted
            );
        });
    }

    private Mono<io.github.storagenode.grpc.upload.FinalizeResponse> finalizeUpload(FinalizeRequest request) {
        return Mono.create(sink -> uploadStub.finalizeUpload(request, new StreamObserver<>() {
            @Override
            public void onNext(io.github.storagenode.grpc.upload.FinalizeResponse response) {
                sink.success(response);
            }

            @Override
            public void onError(Throwable throwable) {
                sink.error(throwable);
            }

            @Override
            public void onCompleted() {
            }
        }));
    }

    @Override
    public Mono<Void> deleteFile(Long userId, String fileId) {
        log.info("Sending Delete Signal to gRPC for file: {}", fileId);
        DeleteFileRequest request = DeleteFileRequest.newBuilder()
                .setFileId(fileId)
                .setUserId(userId.toString())
                .build();

        return deleteFileRpc(request)
                .timeout(Duration.ofSeconds(30))
                .flatMap(response -> {
                    if (response.getSuccess()) {
                        return Mono.empty();
                    }
                    return Mono.error(new IllegalStateException(response.getMessage()));
                });
    }

    private Mono<DeleteFileResponse> deleteFileRpc(DeleteFileRequest request) {
        return Mono.create(sink -> uploadStub.deleteFile(request, new StreamObserver<>() {
            @Override
            public void onNext(DeleteFileResponse response) {
                sink.success(response);
            }

            @Override
            public void onError(Throwable throwable) {
                sink.error(throwable);
            }

            @Override
            public void onCompleted() {
            }
        }));
    }
}
