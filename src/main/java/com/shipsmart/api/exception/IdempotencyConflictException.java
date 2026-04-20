package com.shipsmart.api.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String key) {
        super("Idempotency-Key " + key + " was previously used with a different request body");
    }
}
