package com.payflow.payflow.idempotency;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "merchant_id", length = 36, nullable = false)
    private String merchantId;

    @Column(name = "idem_key", length = 64, nullable = false)
    private String idemKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 16, nullable = false)
    private IdempotencyStatus status;

    @Column(name = "response_code")
    private Integer responseCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payment_id", length = 36)
    private String paymentId;

    @Column(name = "locked_at", nullable = false)
    private LocalDateTime lockedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected IdempotencyRecord() { }

    public IdempotencyRecord(String merchantId, String idemKey, String requestHash,
                             LocalDateTime now, LocalDateTime expiresAt) {
        this.merchantId = merchantId;
        this.idemKey = idemKey;
        this.requestHash = requestHash;
        this.status = IdempotencyStatus.IN_PROGRESS;
        this.lockedAt = now;
        this.expiresAt = expiresAt;
    }

    public void markCompleted(String paymentId, int responseCode) {
        this.status = IdempotencyStatus.COMPLETED;
        this.paymentId = paymentId;
        this.responseCode = responseCode;
    }

    public String getRequestHash() { return requestHash; }
    public IdempotencyStatus getStatus() { return status; }
    public String getPaymentId() { return paymentId; }
}
