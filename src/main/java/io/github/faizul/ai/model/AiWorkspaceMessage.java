package io.github.faizul.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ai_workspace_messages")
public class AiWorkspaceMessage implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID chatId;

    private String role; // 'USER' atau 'ASSISTANT'

    private String content;

    @CreatedDate
    private Instant createdAt;

    @Transient
    @Builder.Default
    private boolean isNewRecord = true;

    @Override
    public boolean isNew() {
        return isNewRecord || createdAt == null;
    }
}
