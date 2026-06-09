package io.github.faizul.File.core;

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
@Table("files")
public class File implements Persistable<UUID> {

    @Id
    private UUID id;

    private String originalFileName;

    private String storageName;

    private Long size;

    //private Boolean active;

    private Long userId;

    @CreatedDate
    private Instant createdAt;

    @Override
    public boolean isNew() {
        return createdAt == null;
    }
}
