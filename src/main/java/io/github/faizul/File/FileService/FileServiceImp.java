package io.github.faizul.File.FileService;

import io.github.faizul.File.Dtos.FileResponse;
import io.github.faizul.Infra.Security.CurrentUserContext;
import io.github.faizul.Storage.Upload.UploadStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.NoSuchElementException;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class FileServiceImp implements FileService {

    private final FileRepository fileRepository;
    private final CurrentUserContext currentUserContext;
    private final UploadStorageService uploadStorageClient;

    @Override
    public Mono<FileResponse> findByUUID(UUID uuid) {
        return currentUserContext.getUserId()
                .flatMap(userId ->
                        fileRepository.findById(uuid)
                                .switchIfEmpty(
                                        Mono.error(new NoSuchElementException("File Not Found!"))
                                )
                                .flatMap(file -> {
                                    if (!file.getUserId().equals(userId)) {
                                        return Mono.error(new AccessDeniedException("Access Denied"));
                                    }
                                    return Mono.just(file);
                                })
                )
                .map(file -> new FileResponse(
                        file.getId(),
                        file.getOriginalFileName(),
                        file.getSize(),
                        file.getCreatedAt()
                ));
    }

    @Override
    public Mono<Void> deleteByUUID(UUID uuid) {
        return currentUserContext.getUserId()
                .flatMap(userId ->
                        fileRepository.findById(uuid)
                                .switchIfEmpty(
                                        Mono.error(new NoSuchElementException("File Not Found!"))
                                )
                                .flatMap(file -> {
                                    if (!file.getUserId().equals(userId)) {
                                        return Mono.error(new AccessDeniedException("Access Denied"));
                                    }
                                    return Mono.just(file);
                                })
                )
                .flatMap(file ->
                        uploadStorageClient.deleteFile(file.getUserId(), file.getId().toString())
                                .then(
                                        fileRepository.deleteById(file.getId())
                                )
                );
    }

    @Override
    public Flux<FileResponse> getAllByUserId() {
        return currentUserContext.getUserId()
                .flatMapMany(userId -> fileRepository.findByUserId(userId))
                .map(file -> new FileResponse(
                        file.getId(),
                        file.getOriginalFileName(),
                        file.getSize(),
                        file.getCreatedAt()
                ));
    }

    @Override
    public Flux<FileResponse> getAllForAdmin() {
        return fileRepository.findAll()
                .map(file -> new FileResponse(
                        file.getId(),
                        file.getOriginalFileName(),
                        file.getSize(),
                        file.getCreatedAt()
                ));
    }
}
