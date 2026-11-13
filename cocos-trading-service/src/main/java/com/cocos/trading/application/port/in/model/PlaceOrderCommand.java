package com.cocos.trading.application.port.in.model;

import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderType;
import java.math.BigDecimal;
import lombok.Builder;

/**
 * Command model encapsulating all parameters required to submit a trading order.
 *
 * @param userId       ID of the user submitting the order.
 * @param instrumentId ID of the target instrument.
 * @param side         Order side ({@link OrderSide#BUY}, {@link OrderSide#SELL}, {@link OrderSide#CASH_IN}, {@link OrderSide#CASH_OUT}).
 * @param type         Order execution type ({@link OrderType#MARKET} or {@link OrderType#LIMIT}).
 * @param size         Optional exact quantity of shares/units.
 * @param amount       Optional total investment amount in ARS.
 * @param price        Optional target price in ARS (required for {@link OrderType#LIMIT}).
 */
@Builder
public record PlaceOrderCommand(
        Long userId,
        Long instrumentId,
        OrderSide side,
        OrderType type,
        Integer size,
        BigDecimal amount,
        BigDecimal price
) {
}

