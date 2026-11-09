package com.payflow.payflow.payment;

public interface PaymentRecorder {
    /** Saves the payment and, in the same database step, its diary entry and webhook row. */
    Payment save(Payment payment, PaymentStatus from);
}