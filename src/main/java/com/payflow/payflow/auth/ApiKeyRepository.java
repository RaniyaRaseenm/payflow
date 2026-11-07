package com.payflow.payflow.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {
    Optional<ApiKey> findByKeyPrefixAndStatus(String keyPrefix, ApiKeyStatus status);
}
