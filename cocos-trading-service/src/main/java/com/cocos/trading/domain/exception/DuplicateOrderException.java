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
                "Duplicate order request rejected: Idempotency-Key '%s' has already been processed or is currently in progress.",
                idempotencyKey
        ));
        this.idempotencyKey = idempotencyKey;
    }
}

