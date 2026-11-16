package com.cocos.trading.domain.exception;

import java.util.UUID;
import lombok.Getter;

/**
 * Thrown when an incoming order request carries an {@code Idempotency-Key} that has already
 * been processed or is currently locked in-flight within the idempotency TTL window.
 */
@Getter
public class DuplicateOrderException extends BusinessRuleViolationException {

    private final UUID idempotencyKey;

    public DuplicateOrderException(UUID idempotencyKey) {
        super(String.format(
                "Order with Idempotency-Key '%s' is currently being processed. Please wait for the in-flight request to complete or retry.",
                idempotencyKey
        ));
        this.idempotencyKey = idempotencyKey;
    }
}

