package com.cocos.trading.domain.exception;

import java.util.UUID;

/**
 * Thrown when a client reuses an {@code Idempotency-Key} with a different
 * request payload than the one originally persisted (Stripe-style Idempotency
 * Mismatch protection).
 */
public class IdempotencyMismatchException extends DomainException {

    private static final String DEFAULT_MESSAGE =
            "Keys for idempotent requests can only be used with the same parameters they were first used with";

    private final UUID idempotencyKey;

    public IdempotencyMismatchException(UUID idempotencyKey) {
        super(DEFAULT_MESSAGE);
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }
}

