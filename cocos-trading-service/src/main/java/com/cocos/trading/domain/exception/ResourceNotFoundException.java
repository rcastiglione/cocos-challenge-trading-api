package com.cocos.trading.domain.exception;

/**
 * Exception thrown when a requested domain entity (User, Instrument, Order, or MarketData) cannot be found.
 */
public class ResourceNotFoundException extends DomainException {

    private final String resourceName;
    private final Object identifier;

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(String.format("%s not found with identifier: %s", resourceName, identifier));
        this.resourceName = resourceName;
        this.identifier = identifier;
    }

    public String getResourceName() {
        return resourceName;
    }

    public Object getIdentifier() {
        return identifier;
    }
}

