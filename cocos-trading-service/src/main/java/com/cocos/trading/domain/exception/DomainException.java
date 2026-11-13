package com.cocos.trading.domain.exception;

/**
 * Base runtime exception for all domain-level errors in the trading engine.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}

