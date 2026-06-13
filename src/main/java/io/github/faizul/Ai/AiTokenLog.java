package io.github.faizul.Ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ai_token_logs")
public class AiTokenLog {

    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("activity_type")
    private String activityType;

    @Column("provider")
    private String provider;

    @Column("model_name")
    private String modelName;

    @Column("input_tokens")
    private Integer inputTokens;

    @Column("output_tokens")
    private Integer outputTokens;

    @Column("total_tokens")
    private Integer totalTokens;

    @Column("created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
