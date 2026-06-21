package io.github.faizul.storage.share.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("file_shared")
public class FileShared {

    @Id
    private Long id;

    @Column("file_id")
    private UUID fileId;

    @Column("user_id")
    private Long userId; // Nullable for public share

    @Column("expires_at")
    private LocalDateTime expiresAt; // Nullable

    @Column("share_token")
    private String shareToken; // Nullable

    @Column("is_public")
    private Boolean isPublic; // Defaults to false

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;
}
