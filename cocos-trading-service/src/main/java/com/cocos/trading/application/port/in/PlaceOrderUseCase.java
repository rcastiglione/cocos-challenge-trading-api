package com.cocos.trading.application.port.in;

import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.repository.entity.OrderJpaEntity;

/**
 * Inbound port (use case) for placing a new market or limit order.
 */
public interface PlaceOrderUseCase {

    /**
     * Places an order in the trading engine, validating buying power or share holdings
     * and persisting the resulting order (FILLED, NEW, or REJECTED).
     *
     * @param command The order placement command.
     * @return The persisted {@link OrderJpaEntity}.
     */
    OrderJpaEntity placeOrder(PlaceOrderCommand command);
}
