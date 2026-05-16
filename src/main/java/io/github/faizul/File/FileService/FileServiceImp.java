package io.github.faizul.File.FileService;

import io.github.faizul.File.Dtos.FileResponse;
import io.github.faizul.Infra.Security.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class FileServiceImp implements FileService {

    private final FileRepository fileRepository;
    private final CurrentUserContext currentUserContext;

    @Override
    public Mono<FileResponse> findByUUID(UUID uuid) {
        return currentUserContext.getUserId()
                .flatMap(userId ->
                        fileRepository.findById(uuid)
                                .switchIfEmpty(
                                        Mono.error(new RuntimeException("File Not Found!"))
                                )
                                .flatMap(file -> {
                                    if (!file.getUserId().equals(userId)) {
                                        return Mono.error(new RuntimeException("Access Denied"));
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
}
