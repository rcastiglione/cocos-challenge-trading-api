package com.cocos.trading.application.strategy;

import com.cocos.trading.repository.entity.OrderJpaEntity;

/**
 * Strategy interface for routing, sizing, validating, and determining the initial status
 * of a trading order ({@code MARKET}, {@code LIMIT}, or {@code CASH_IN}/{@code CASH_OUT}).
 */
public interface OrderExecutionStrategy {

    /**
     * Evaluates whether this strategy supports the provided execution context.
     *
     * @param context The order execution context.
     * @return {@code true} if this strategy handles the order; {@code false} otherwise.
     */
    boolean supports(OrderExecutionContext context);

    /**
     * Builds the resulting {@link OrderJpaEntity} ready to be persisted in the database
     * (with status {@code FILLED}, {@code NEW}, or {@code REJECTED}).
     *
     * @param context The order execution context.
     * @return The evaluated {@link OrderJpaEntity}.
     */
    OrderJpaEntity execute(OrderExecutionContext context);
}
