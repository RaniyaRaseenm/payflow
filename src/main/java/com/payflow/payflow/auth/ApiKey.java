package com.payflow.payflow.auth;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
public class ApiKey {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private String id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "merchant_id", length = 36, nullable = false)
    private String merchantId;

    @Column(name = "key_prefix", length = 16, nullable = false, unique = true)
    private String keyPrefix;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "key_hash", length = 64, nullable = false)
    private String keyHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 16, nullable = false)
    private ApiKeyStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    protected ApiKey() { }

    public ApiKey(String merchantId, String keyPrefix, String keyHash, LocalDateTime now) {
        this.id = UUID.randomUUID().toString();
        this.merchantId = merchantId;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.status = ApiKeyStatus.ACTIVE;
        this.createdAt = now;
    }

    public String getMerchantId() { return merchantId; }
    public String getKeyHash() { return keyHash; }
    public ApiKeyStatus getStatus() { return status; }
    public void revoke() { this.status = ApiKeyStatus.REVOKED; }
}
