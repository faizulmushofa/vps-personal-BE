package io.github.faizul.security.jwt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("refresh_token")
@EnableR2dbcAuditing
public class RefreshToken {
    @Id
    private Long id;

    private Long userId;

    private String token;

    private Boolean revoked;

    @LastModifiedDate
    LocalDateTime expiredAt;

    @CreatedDate
    LocalDateTime createdAt;



}
