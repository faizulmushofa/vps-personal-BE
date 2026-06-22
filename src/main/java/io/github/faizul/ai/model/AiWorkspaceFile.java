package io.github.faizul.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ai_workspace_files")
public class AiWorkspaceFile implements Persistable<String> {

    private UUID workspaceId;

    private UUID fileId;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Transient
    @Builder.Default
    private boolean isNewRecord = true;

    @Override
    public String getId() {
        return workspaceId + "_" + fileId;
    }

    @Override
    public boolean isNew() {
        return isNewRecord;
    }
}
