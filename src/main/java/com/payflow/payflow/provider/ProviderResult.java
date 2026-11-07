package com.payflow.payflow.provider;

public record ProviderResult(boolean success, String providerRef, String failureCode) {

    public static ProviderResult ok(String providerRef) {
        return new ProviderResult(true, providerRef, null);
    }

    public static ProviderResult declined(String failureCode) {
        return new ProviderResult(false, null, failureCode);
    }
}
