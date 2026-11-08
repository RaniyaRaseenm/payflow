package com.payflow.payflow.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByMerchantIdAndIdemKey(String merchantId, String idemKey);
}
