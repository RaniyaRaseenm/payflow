package com.payflow.payflow.provider;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MockPaymentProvider implements PaymentProvider {

    // Same idempotency key always gives the same answer, like a real provider.
    private final Map<String, ProviderResult> seen = new ConcurrentHashMap<>();
    private final AtomicInteger flakyCalls = new AtomicInteger();

    @Override
    public ProviderResult authorize(String idempotencyKey, long amountMinor,
                                    String currency, String token) {
        return seen.computeIfAbsent("auth:" + idempotencyKey,
                k -> decide(token));
    }

    @Override
    public ProviderResult capture(String idempotencyKey, String providerRef) {
        return seen.computeIfAbsent("cap:" + idempotencyKey, k -> {
            if (providerRef != null && providerRef.startsWith("prov_capfail")) {
                return ProviderResult.declined("CAPTURE_FAILED");
            }
            return ProviderResult.ok(providerRef);
        });
    }

    private ProviderResult decide(String token) {
        if (token == null) {
            return ProviderResult.declined("INVALID_TOKEN");
        }
        switch (token) {
            case "tok_visa":
                return ProviderResult.ok("prov_" + UUID.randomUUID());
            case "tok_declined":
                return ProviderResult.declined("CARD_DECLINED");
            case "tok_insufficient_funds":
                return ProviderResult.declined("INSUFFICIENT_FUNDS");
            case "tok_capture_fails":
                return ProviderResult.ok("prov_capfail_" + UUID.randomUUID());
            case "tok_flaky":
                if (flakyCalls.incrementAndGet() <= 2) {
                    throw new ProviderUnavailableException("Provider returned 503");
                }
                return ProviderResult.ok("prov_" + UUID.randomUUID());
            default:
                return ProviderResult.declined("INVALID_TOKEN");
        }
    }
}