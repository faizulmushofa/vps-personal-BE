package io.github.faizul.File.ShareService;

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
@Table("file_shared")
public class FileShared implements Persistable<Long> {

    @Id
    private Long id;

    private UUID fileId;

    private Long userId;

    @CreatedDate
    private Instant createdAt;

    @Override
    public boolean isNew() {
        return id == null;
    }
}
