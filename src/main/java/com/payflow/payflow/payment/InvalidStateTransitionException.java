package com.payflow.payflow.payment;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Cannot move payment from " + from + " to " + to);
    }
}
