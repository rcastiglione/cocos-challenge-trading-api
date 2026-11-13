package com.cocos.trading.domain.exception;

/**
 * Exception thrown when an order request violates a business rule (e.g., invalid side for instrument type).
 */
public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}

