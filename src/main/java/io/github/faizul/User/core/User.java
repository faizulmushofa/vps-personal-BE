package io.github.faizul.User.core;

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
}
