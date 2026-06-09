package io.github.faizul.File.core.NodeStorage;

import io.github.faizul.File.core.FileRepository;
import io.github.faizul.File.core.FileService;

import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.Storage.upload.UploadStorageService;
import io.github.faizul.User.core.UserRepository;
import io.github.faizul.File.dtos.UserProfileResponse;
import io.github.faizul.File.dtos.UserStorageResponse;
import io.github.faizul.File.dtos.UserStorageSummary;
import io.github.faizul.File.dtos.UpdateQuotaRequest;
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
    private final UserRepository userRepository;

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

    @Override
    public Mono<UserProfileResponse> getCurrentUserProfile() {
        return currentUserContext.getUserId()
                .flatMap(userRepository::findById)
                .map(user -> new UserProfileResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail()
                ));
    }

    @Override
    public Mono<UserStorageResponse> getCurrentUserStorage() {
        return currentUserContext.getUserId()
                .flatMap(userId -> userRepository.findById(userId)
                        .flatMap(user -> fileRepository.calculateUsedStorageByUserId(userId)
                                .map(usedBytes -> new UserStorageResponse(
                                        usedBytes,
                                        user.getStorageQuota() != null ? user.getStorageQuota() : 1073741824L
                                ))
                        )
                );
    }

    @Override
    public Flux<UserStorageSummary> getUserStorageSummary() {
        return userRepository.findAll()
                .flatMap(user -> fileRepository.calculateUsedStorageByUserId(user.getId())
                        .map(usedBytes -> new UserStorageSummary(
                                user.getId(),
                                user.getUsername(),
                                user.getEmail(),
                                usedBytes,
                                user.getStorageQuota() != null ? user.getStorageQuota() : 1073741824L
                        ))
                );
    }

    @Override
    public Mono<UserStorageSummary> updateUserQuota(Long id, UpdateQuotaRequest request) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("User Not Found")))
                .flatMap(user -> {
                    user.setStorageQuota(request.quotaBytes());
                    return userRepository.save(user);
                })
                .flatMap(user -> fileRepository.calculateUsedStorageByUserId(user.getId())
                        .map(usedBytes -> new UserStorageSummary(
                                user.getId(),
                                user.getUsername(),
                                user.getEmail(),
                                usedBytes,
                                user.getStorageQuota()
                        ))
                );
    }
}
