package io.github.faizul.Storage.download;


import io.github.faizul.File.dtos.FileChunk;
import io.github.storagenode.grpc.download.DownloadRequest;
import io.github.storagenode.grpc.download.DownloadResponse;
import io.github.storagenode.grpc.download.DownloadServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

import java.util.UUID;

@Component
public class DownloadStorageImp implements DownloadStorageService {

    private final DownloadServiceGrpc.DownloadServiceStub downloadStub;
    private final Scheduler grpcDispatchScheduler;

    public DownloadStorageImp(GrpcChannelFactory channels, Scheduler grpcDispatchScheduler) {
        ManagedChannel channel = channels.createChannel("storage-node");
        this.downloadStub = DownloadServiceGrpc.newStub(channel);
        this.grpcDispatchScheduler = grpcDispatchScheduler;
    }

    @Override
    public Flux<FileChunk> downloadFile(Long userId, UUID id) {
        return Flux.<FileChunk>create(sink -> {
            DownloadRequest request = DownloadRequest.newBuilder()
                    .setFileId(id.toString())
                    .setUserId(userId.toString())
                    .build();

            StreamObserver<DownloadResponse> responseObserver = new StreamObserver<>() {
                @Override
                public void onNext(DownloadResponse response) {
                    sink.next(new FileChunk(
                            response.getFileId(),
                            response.getChunkIndex(),
                            response.getTotalChunks(),
                            response.getData().toByteArray()
                    ));
                }

                @Override
                public void onError(Throwable throwable) {
                    sink.error(throwable);
                }

                @Override
                public void onCompleted() {
                    sink.complete();
                }
            };

            downloadStub.downloadFile(request, responseObserver);
        }).subscribeOn(grpcDispatchScheduler);
    }
}

