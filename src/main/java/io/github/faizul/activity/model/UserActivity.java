package io.github.faizul.activity.model;

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
@Table("user_activities")
public class UserActivity {

    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("activity_type")
    private String activityType;

    @Column("description")
    private String description;

    @Column("ip_address")
    private String ipAddress;

    @Column("created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
