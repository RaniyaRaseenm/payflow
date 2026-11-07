package com.payflow.payflow.payment;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String id) {
        super("Payment not found: " + id);
    }
}
