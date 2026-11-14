package com.cocos.trading.application.port.in.model;

import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderType;
import java.util.UUID;
import lombok.Builder;

/**
 * Command model encapsulating all parameters required to submit a trading order.
 *
 * @param idempotencyKey Mandatory client-generated UUID preventing duplicate order execution across retries.
 * @param userId         ID of the user submitting the order.
 * @param instrumentId   ID of the target instrument.
 * @param side           Order side ({@link OrderSide#BUY}, {@link OrderSide#SELL}, {@link OrderSide#CASH_IN}, {@link OrderSide#CASH_OUT}).
 * @param type           Order execution type ({@link OrderType#MARKET} or {@link OrderType#LIMIT}).
 * @param size           Optional exact quantity of shares/units.
 * @param amount         Optional total investment amount in ARS as a {@link MonetaryAmount}.
 * @param price          Optional target price in ARS as a {@link MonetaryAmount} (required for {@link OrderType#LIMIT}).
 */
@Builder
public record PlaceOrderCommand(
        UUID idempotencyKey,
        Long userId,
        Long instrumentId,
        OrderSide side,
        OrderType type,
        Integer size,
        MonetaryAmount amount,
        MonetaryAmount price
) {
}
