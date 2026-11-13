package com.cocos.trading.application.port.in;

import com.cocos.trading.domain.model.Order;

/**
 * Inbound port (use case) for cancelling an active order.
 */
public interface CancelOrderUseCase {

    /**
     * Cancels an order if its current status is {@code NEW}.
     *
     * @param orderId The unique identifier of the order to cancel.
     * @return The updated {@link Order} with status {@code CANCELLED}.
     */
    Order cancelOrder(Long orderId);
}

