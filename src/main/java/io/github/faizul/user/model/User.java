package io.github.faizul.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("users")
public class User {

    @Id
    private Long id;

    @Column("username")
    private String username;

    @Column("email")
    private String email;

    @Column("password")
    private String password;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("storage_quota")
    @Builder.Default
    private Long storageQuota = 1073741824L;

    @Column("full_name")
    private String fullName;

    @Column("avatar_url")
    private String avatarUrl;

    @Column("phone_number")
    private String phoneNumber;

    @Column("is_active")
    private Boolean isActive;

    @Column("deleted_at")
    private LocalDateTime deletedAt;

    @Column("ai_daily_limit")
    @Builder.Default
    private Integer aiDailyLimit = 5;

    @Column("migration_daily_limit")
    @Builder.Default
    private Integer migrationDailyLimit = 3;

    @Column("migration_max_file_size")
    @Builder.Default
    private Long migrationMaxFileSize = 268435456L;

    @Column("daily_ai_requests")
    @Builder.Default
    private Integer dailyAiRequests = 0;

    @Column("last_ai_request_date")
    private java.time.LocalDate lastAiRequestDate;

    @Column("subscription_tier")
    @Builder.Default
    private String subscriptionTier = "FREEMIUM";

    @Column("subscription_expires_at")
    private LocalDateTime subscriptionExpiresAt;

    @Column("academic_email")
    private String academicEmail;

    @Column("student_verified")
    @Builder.Default
    private Boolean studentVerified = false;

    public SubscriptionPlanType getSubscriptionPlan() {
        return SubscriptionPlanType.getPlan(this.subscriptionTier);
    }
}
