package com.cocos.trading.domain.model;

/**
 * Enumeration representing the side of a trading order.
 */
public enum OrderSide {
    BUY,
    SELL,
    CASH_IN,
    CASH_OUT;

    /**
     * Checks whether this side represents a cash transfer operation (CASH_IN or CASH_OUT).
     *
     * @return {@code true} if CASH_IN or CASH_OUT; {@code false} otherwise.
     */
    public boolean isCashOperation() {
        return this == CASH_IN || this == CASH_OUT;
    }
}

