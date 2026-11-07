package com.payflow.payflow.merchant;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private String id;

    @Column(length = 120, nullable = false)
    private String name;

    @Column(length = 190, nullable = false, unique = true)
    private String email;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "webhook_secret", length = 100)
    private String webhookSecret;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Merchant() { }

    public Merchant(String name, String email, String webhookUrl,
                    String webhookSecret, LocalDateTime now) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.email = email;
        this.webhookUrl = webhookUrl;
        this.webhookSecret = webhookSecret;
        this.createdAt = now;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getWebhookUrl() { return webhookUrl; }
    public String getWebhookSecret() { return webhookSecret; }
}
