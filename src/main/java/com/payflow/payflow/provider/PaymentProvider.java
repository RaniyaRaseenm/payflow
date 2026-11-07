package com.payflow.payflow.provider;

public interface PaymentProvider {
    ProviderResult authorize(String idempotencyKey, long amountMinor,
                             String currency, String token);
    ProviderResult capture(String idempotencyKey, String providerRef);
}