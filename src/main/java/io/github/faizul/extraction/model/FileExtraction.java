package io.github.faizul.extraction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("file_extractions")
public class FileExtraction implements Persistable<UUID> {

    @Id
    @Column("file_id")
    private UUID fileId;

    private String extractedText;

    @CreatedDate
    private Instant createdAt;

    @Transient
    private boolean isNewRecord = true;

    @Override
    public UUID getId() {
        return fileId;
    }

    @Override
    public boolean isNew() {
        return isNewRecord || createdAt == null;
    }
}
