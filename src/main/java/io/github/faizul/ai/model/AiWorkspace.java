package io.github.faizul.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ai_workspaces")
public class AiWorkspace implements Persistable<UUID> {

    @Id
    private UUID id;

    private Long userId;

    private String name;

    private String description;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Transient
    @Builder.Default
    private boolean isNewRecord = true;

    @Override
    public boolean isNew() {
        return isNewRecord || createdAt == null;
    }
}
