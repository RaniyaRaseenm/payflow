package com.payflow.payflow.payment;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private String id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "merchant_id", length = 36, nullable = false)
    private String merchantId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 3, nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 16, nullable = false)
    private PaymentStatus status;

    @Column(name = "payment_method_token", length = 64, nullable = false)
    private String paymentMethodToken;

    @Column(name = "provider_ref", length = 64)
    private String providerRef;

    @Column(name = "failure_code", length = 40)
    private String failureCode;

    @Version
    private int version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Payment() { }

    public Payment(String merchantId, long amountMinor, String currency,
                   String paymentMethodToken, LocalDateTime now) {
        this.id = UUID.randomUUID().toString();
        this.merchantId = merchantId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.paymentMethodToken = paymentMethodToken;
        this.status = PaymentStatus.CREATED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void transitionTo(PaymentStatus next, LocalDateTime now) {
        if (!status.canMoveTo(next)) {
            throw new InvalidStateTransitionException(status, next);
        }
        this.status = next;
        this.updatedAt = now;
    }

    public String getId() { return id; }
    public String getMerchantId() { return merchantId; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public int getVersion() { return version; }
    public String getProviderRef() { return providerRef; }
    public void setProviderRef(String providerRef) { this.providerRef = providerRef; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
}
