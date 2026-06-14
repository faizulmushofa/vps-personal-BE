package io.github.faizul.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("migration_tasks")
public class MigrationTask implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column("batch_id")
    private UUID batchId;

    @Column("user_id")
    private Long userId;

    @Column("file_id")
    private UUID fileId;

    @Column("file_name")
    private String fileName;

    @Column("source_provider")
    private String sourceProvider;

    @Column("target_provider")
    private String targetProvider;

    @Column("target_external_account_id")
    private Long targetExternalAccountId;

    @Column("delete_source")
    private Boolean deleteSource;

    @Column("status")
    private MigrationStatus status;

    @Column("progress")
    private Double progress;

    @Column("error_message")
    private String errorMessage;

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Override
    public boolean isNew() {
        return createdAt == null;
    }
}
