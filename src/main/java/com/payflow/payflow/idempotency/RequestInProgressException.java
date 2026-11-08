package com.payflow.payflow.idempotency;

public class RequestInProgressException extends RuntimeException {
    public RequestInProgressException() {
        super("A request with this Idempotency-Key is still being processed");
    }
}
