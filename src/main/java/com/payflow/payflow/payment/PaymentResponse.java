package com.payflow.payflow.payment;

public record PaymentResponse(String id, long amount, String currency,
                              String status, String failureCode, int version) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getAmountMinor(), p.getCurrency(),
                p.getStatus().name(), p.getFailureCode(), p.getVersion());
    }
}
