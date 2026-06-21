package io.github.faizul.security.auth.otp;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface OtpVerificationRepository extends ReactiveCrudRepository<OtpVerification, Long> {

    @Query("SELECT * FROM otp_verifications WHERE email = :email AND type = :type AND verified = false ORDER BY id DESC LIMIT 1")
    Mono<OtpVerification> findLatestUnverified(String email, String type);

    @Modifying
    @Query("UPDATE otp_verifications SET verified = true WHERE email = :email AND type = :type AND verified = false")
    Mono<Void> invalidateAllUnverified(String email, String type);
}
