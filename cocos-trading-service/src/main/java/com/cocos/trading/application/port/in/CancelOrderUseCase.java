package com.cocos.trading.application.port.in;

import com.cocos.trading.repository.entity.OrderJpaEntity;

/**
 * Inbound port (use case) for cancelling an active order.
 */
public interface CancelOrderUseCase {

    /**
     * Cancels an order if its current status is {@code NEW}.
     *
     * @param orderId The unique identifier of the order to cancel.
     * @return The updated {@link OrderJpaEntity} with status {@code CANCELLED}.
     */
    OrderJpaEntity cancelOrder(Long orderId);
}
