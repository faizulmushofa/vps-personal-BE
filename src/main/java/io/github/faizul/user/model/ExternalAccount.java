package io.github.faizul.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table("external_users")
public class ExternalAccount {

    @Id
    private Long id;

    private Long userId;

    private String provider; // GOOGLE

    private String providerUserId; // google sub / email

    private String email;

    private String accessToken;

    private String refreshToken;

    private Long expiresAt;

}
