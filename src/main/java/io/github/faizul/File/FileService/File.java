package io.github.faizul.File.FileService;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("files")
public class File {

    @Id
    private UUID id;

    private String originalFileName;

    private String storageName;

    private Long size;

    private Long userId;

    @CreatedDate
    private Instant createdAt;
}
