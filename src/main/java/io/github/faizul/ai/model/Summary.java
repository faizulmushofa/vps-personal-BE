package io.github.faizul.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("file_summaries")
public class Summary {

    @Id
    private Long id;

    @Column("file_id")
    private UUID fileId;

    @Column("summary")
    private String summary;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;
}
