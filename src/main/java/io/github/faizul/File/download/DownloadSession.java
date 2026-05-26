package io.github.faizul.File.download;

import io.github.faizul.File.core.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@Table("download_sessions")
public class DownloadSession implements Persistable<UUID> {
    @Id
    private UUID id;

    private UUID fileId;

    private Long userId;

    private FileStatus status;

    private Long totalBytes;

    private Long bytesSent;

    private Instant startedAt;

    private Instant completedAt;

    @CreatedDate
    private Instant createdAt;


    @Override
    public boolean isNew() {
        return this.createdAt == null;
    }
}
