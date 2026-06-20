package io.github.faizul.storage.upload.model;

import io.github.faizul.storage.file.model.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("upload_sessions")
public class UploadSession implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID fileId;

    private String tempPath;

    private Integer totalChunks;

    private Integer uploadedChunks;

    private FileStatus status;

    private Instant completedAt;

    @CreatedDate
    private Instant createdAt;

    @Override
    public boolean isNew() {
        return createdAt == null;
    }

}
