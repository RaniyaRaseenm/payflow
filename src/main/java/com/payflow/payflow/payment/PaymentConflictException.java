package com.payflow.payflow.payment;

public class PaymentConflictException extends RuntimeException {
    public PaymentConflictException() {
        super("The payment was changed by another request. Please retry.");
    }
}
