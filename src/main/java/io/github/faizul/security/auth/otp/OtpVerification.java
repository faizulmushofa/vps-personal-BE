package io.github.faizul.security.auth.otp;

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
@Table("otp_verifications")
public class OtpVerification {

    @Id
    private Long id;

    @Column("email")
    private String email;

    @Column("otp_code")
    private String otpCode;

    @Column("type")
    private String type; 

    @Column("expiry_time")
    private LocalDateTime expiryTime;

    @Column("verified")
    private Boolean verified;
}
