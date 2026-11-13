package com.cocos.trading.domain.exception;

import com.cocos.trading.domain.model.OrderStatus;

/**
 * Exception thrown when an operation is attempted on an order in an incompatible state
 * (e.g., attempting to cancel an order whose status is not {@link OrderStatus#NEW}).
 */
public class InvalidOrderStateException extends DomainException {

    private final Long orderId;
    private final OrderStatus currentStatus;

    public InvalidOrderStateException(Long orderId, OrderStatus currentStatus, String message) {
        super(message);
        this.orderId = orderId;
        this.currentStatus = currentStatus;
    }

    public Long getOrderId() {
        return orderId;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }
}

