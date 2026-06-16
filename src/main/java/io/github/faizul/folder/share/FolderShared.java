package io.github.faizul.folder.share;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("folder_shared")
public class FolderShared {

    @Id
    private Long id;

    private String folderId;

    private String folderType;

    private Long userId;

    private Instant expiresAt;

    private String shareToken;

    private String permission; // "VIEW" or "EDIT"

    private Boolean allowAnonymous; // default true

    private String targetStorage; // "LOCAL" or "GOOGLE_DRIVE"

    private Long externalAccountId;

    @CreatedDate
    private Instant createdAt;
}
