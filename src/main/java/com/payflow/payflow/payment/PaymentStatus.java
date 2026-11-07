package com.payflow.payflow.payment;

import java.util.Set;

public enum PaymentStatus {
    CREATED, AUTHORIZED, CAPTURED, REFUNDED, FAILED, VOIDED;

    public Set<PaymentStatus> allowedNext() {
        return switch (this) {
            case CREATED -> Set.of(AUTHORIZED, FAILED);
            case AUTHORIZED -> Set.of(CAPTURED, VOIDED, FAILED);
            case CAPTURED -> Set.of(REFUNDED);
            case FAILED, VOIDED, REFUNDED -> Set.of();
        };
    }

    public boolean canMoveTo(PaymentStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }
}

