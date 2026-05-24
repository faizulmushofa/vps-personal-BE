package io.github.faizul.File.ShareService;

import io.github.faizul.File.Dtos.FileResponse;
import io.github.faizul.File.FileService.FileRepository;
import io.github.faizul.Infra.Security.CurrentUserContext;
import io.github.faizul.User.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ShareServiceImp implements ShareService {

    private final FileRepository fileRepository;
    private final FileSharedRepository fileSharedRepository;
    private final CurrentUserContext currentUserContext;
    private final UserRepository userRepository;

    @Override
    public Mono<Void> shareFile(UUID fileId, String targetEmail) {
        return currentUserContext.getUserId()
                .flatMap(userId -> fileRepository.findById(fileId)
                        .switchIfEmpty(Mono.error(new RuntimeException("File Not Found")))
                        .flatMap(file -> {
                            if (!file.getUserId().equals(userId)) {
                                return Mono.error(new RuntimeException("Only owner can share the file"));
                            }
                            return userRepository.findByEmail(targetEmail)
                                    .switchIfEmpty(Mono.error(new RuntimeException("User with email not found")))
                                    .flatMap(targetUser -> {
                                        if (userId.equals(targetUser.getId())) {
                                            return Mono.error(new RuntimeException("Cannot share with yourself"));
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
                        .switchIfEmpty(Mono.error(new RuntimeException("File Not Found")))
                        .flatMap(file -> {
                            if (!file.getUserId().equals(userId)) {
                                return Mono.error(new RuntimeException("Only owner can unshare the file"));
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
                .switchIfEmpty(Mono.error(new RuntimeException("File Not Found")))
                .flatMap(file -> {
                    if (file.getUserId().equals(userId)) {
                        return Mono.just(true);
                    }
                    return fileSharedRepository.existsByFileIdAndUserId(fileId, userId);
                });
    }
}
