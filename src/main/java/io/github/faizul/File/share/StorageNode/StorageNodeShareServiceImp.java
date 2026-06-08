package io.github.faizul.File.share.StorageNode;

import io.github.faizul.File.share.FileShared;
import io.github.faizul.File.share.FileSharedRepository;
import io.github.faizul.File.share.ShareService;

import io.github.faizul.File.dtos.FileResponse;
import io.github.faizul.File.core.FileRepository;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.User.core.UserRepository;
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
public class StorageNodeShareServiceImp implements ShareService {

    private final FileRepository fileRepository;
    private final FileSharedRepository fileSharedRepository;
    private final CurrentUserContext currentUserContext;
    private final UserRepository userRepository;

    @Override
    public Mono<Void> shareFile(UUID fileId, String targetEmail) {
        return currentUserContext.getUserId()
                .flatMap(userId -> fileRepository.findById(fileId)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("File Not Found")))
                        .flatMap(file -> {
                            if (!file.getUserId().equals(userId)) {
                                return Mono.error(new AccessDeniedException("Only owner can share the file"));
                            }
                            return userRepository.findByEmail(targetEmail)
                                    .switchIfEmpty(Mono.error(new NoSuchElementException("User with email not found")))
                                    .flatMap(targetUser -> {
                                        if (userId.equals(targetUser.getId())) {
                                            return Mono.error(new IllegalArgumentException("Cannot share with yourself"));
                                        }
                                        return fileSharedRepository.existsByFileIdAndUserId(fileId, targetUser.getId())
                                                .flatMap(exists -> {
                                                    if (exists) return Mono.empty();
                                                    FileShared shared = FileShared.builder()
                                                            .fileId(fileId)
                                                            .userId(targetUser.getId())
                                                            .build();
                                                    return fileSharedRepository.save(shared).then();
                                                });
                                    });
                        }));
    }

    @Override
    public Mono<Void> unshareFile(UUID fileId, Long targetUserId) {
        return currentUserContext.getUserId()
                .flatMap(userId -> fileRepository.findById(fileId)
                        .switchIfEmpty(Mono.error(new NoSuchElementException("File Not Found")))
                        .flatMap(file -> {
                            if (!file.getUserId().equals(userId)) {
                                return Mono.error(new AccessDeniedException("Only owner can unshare the file"));
                            }
                            return fileSharedRepository.deleteByFileIdAndUserId(fileId, targetUserId);
                        }));
    }

    @Override
    public Flux<FileResponse> getSharedWithMe() {
        return currentUserContext.getUserId()
                .flatMapMany(userId -> fileSharedRepository.findByUserId(userId)
                        .flatMap(shared -> fileRepository.findById(shared.getFileId()))
                )
                .map(file -> new FileResponse(
                        file.getId(),
                        file.getOriginalFileName(),
                        file.getSize(),
                        file.getCreatedAt()
                ));
    }

    @Override
    public Mono<Boolean> hasReadAccess(UUID fileId, Long userId) {
        return fileRepository.findById(fileId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("File Not Found")))
                .flatMap(file -> {
                    if (file.getUserId().equals(userId)) {
                        return Mono.just(true);
                    }
                    return fileSharedRepository.existsByFileIdAndUserId(fileId, userId);
                });
    }
}
