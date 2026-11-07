CREATE TABLE merchants (
                           id CHAR(36) PRIMARY KEY,
                           name VARCHAR(120) NOT NULL,
                           email VARCHAR(190) NOT NULL,
                           webhook_url VARCHAR(500) NULL,
                           webhook_secret VARCHAR(100) NULL,
                           created_at DATETIME(3) NOT NULL,
                           UNIQUE KEY uq_merchant_email (email)
);

CREATE TABLE api_keys (
                          id CHAR(36) PRIMARY KEY,
                          merchant_id CHAR(36) NOT NULL,
                          key_prefix VARCHAR(16) NOT NULL,
                          key_hash CHAR(64) NOT NULL,
                          status VARCHAR(16) NOT NULL,
                          created_at DATETIME(3) NOT NULL,
                          last_used_at DATETIME(3) NULL,
                          UNIQUE KEY uq_key_prefix (key_prefix),
                          KEY idx_prefix_status (key_prefix, status),
                          CONSTRAINT fk_apikey_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id)
);

CREATE TABLE payments (
                          id CHAR(36) PRIMARY KEY,
                          merchant_id CHAR(36) NOT NULL,
                          amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
                          currency CHAR(3) NOT NULL,
                          status VARCHAR(16) NOT NULL,
                          payment_method_token VARCHAR(64) NOT NULL,
                          provider_ref VARCHAR(64) NULL,
                          failure_code VARCHAR(40) NULL,
                          version INT NOT NULL DEFAULT 0,
                          created_at DATETIME(3) NOT NULL,
                          updated_at DATETIME(3) NOT NULL,
                          KEY idx_pay_merchant_created (merchant_id, created_at),
                          KEY idx_pay_merchant_status (merchant_id, status),
                          CONSTRAINT fk_pay_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id)
);

CREATE TABLE idempotency_keys (
                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                  merchant_id CHAR(36) NOT NULL,
                                  idem_key VARCHAR(64) NOT NULL,
                                  request_hash CHAR(64) NOT NULL,
                                  status VARCHAR(16) NOT NULL,
                                  response_code INT NULL,
                                  response_body JSON NULL,
                                  payment_id CHAR(36) NULL,
                                  locked_at DATETIME(3) NOT NULL,
                                  expires_at DATETIME(3) NOT NULL,
                                  UNIQUE KEY uq_merchant_key (merchant_id, idem_key),
                                  KEY idx_idem_expires (expires_at)
);

CREATE TABLE payment_events (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                payment_id CHAR(36) NOT NULL,
                                from_status VARCHAR(16) NULL,
                                to_status VARCHAR(16) NOT NULL,
                                reason VARCHAR(200) NULL,
                                created_at DATETIME(3) NOT NULL,
                                KEY idx_event_payment_created (payment_id, created_at)
);

CREATE TABLE webhook_deliveries (
                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    merchant_id CHAR(36) NOT NULL,
                                    payment_id CHAR(36) NOT NULL,
                                    event_id VARCHAR(64) NOT NULL,
                                    event_type VARCHAR(40) NOT NULL,
                                    payload JSON NOT NULL,
                                    target_url VARCHAR(500) NOT NULL,
                                    status VARCHAR(16) NOT NULL,
                                    attempt_count INT NOT NULL DEFAULT 0,
                                    next_attempt_at DATETIME(3) NOT NULL,
                                    lease_until DATETIME(3) NULL,
                                    last_http_status INT NULL,
                                    last_error VARCHAR(500) NULL,
                                    created_at DATETIME(3) NOT NULL,
                                    delivered_at DATETIME(3) NULL,
                                    UNIQUE KEY uq_event_id (event_id),
                                    KEY idx_wh_status_next (status, next_attempt_at)
);