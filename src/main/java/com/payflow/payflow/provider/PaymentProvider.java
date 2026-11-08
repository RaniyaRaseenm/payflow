package com.payflow.payflow.provider;

public interface PaymentProvider {
    ProviderResult authorize(String idempotencyKey, long amountMinor,
                             String currency, String token);
    ProviderResult capture(String idempotencyKey, String providerRef);
    ProviderResult voidAuthorization(String idempotencyKey, String providerRef);
    ProviderResult refund(String idempotencyKey, String providerRef);
}