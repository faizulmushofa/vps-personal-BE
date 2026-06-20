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
@Table("subscription_requests")
public class SubscriptionRequest {

    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("requested_tier")
    private String requestedTier;

    @Column("status")
    @Builder.Default
    private String status = "PENDING";

    @Column("xendit_invoice_id")
    private String xenditInvoiceId;

    @Column("invoice_url")
    private String invoiceUrl;

    @Column("external_id")
    private String externalId;

    @Column("amount")
    private Long amount;

    @Column("payment_status")
    @Builder.Default
    private String paymentStatus = "PENDING";

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;
}
