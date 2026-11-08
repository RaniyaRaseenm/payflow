package com.payflow.payflow.idempotency;

public class IdempotencyKeyMissingException extends RuntimeException {
    public IdempotencyKeyMissingException() {
        super("Idempotency-Key header is required (1 to 64 characters)");
    }
}
