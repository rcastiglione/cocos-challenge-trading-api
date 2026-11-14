package com.cocos.trading.domain.model;

import com.cocos.trading.domain.exception.BusinessRuleViolationException;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Domain model representing a market trade order ({@link OrderSide#BUY} or {@link OrderSide#SELL})
 * executed as {@link OrderType#MARKET} or {@link OrderType#LIMIT} on an equity instrument ({@code ACCIONES}).
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
public final class TradeOrder extends Order {

    public TradeOrder(
            Long id,
            Long instrumentId,
            Long userId,
            Integer size,
            MonetaryAmount price,
            OrderType type,
            OrderSide side,
            OrderStatus status,
            LocalDateTime datetime
    ) {
        super(id, instrumentId, userId, size, price, type, validateTradeSide(side), status, datetime);
    }

    protected TradeOrder(TradeOrderBuilder<?, ?> builder) {
        super(builder);
        validateTradeSide(getSide());
    }

    private static OrderSide validateTradeSide(OrderSide side) {
        if (side == null || side.isCashOperation()) {
            throw new BusinessRuleViolationException(
                    "TradeOrder requires a market trade side (BUY or SELL), but received: " + side
            );
        }
        return side;
    }

    @Override
    public MonetaryAmount getCashImpact() {
        if (getSize() == null || getPrice() == null) {
            return MonetaryAmount.ZERO_ARS;
        }
        MonetaryAmount total = getTotalAmount();
        return getSide() == OrderSide.SELL ? total : total.negate();
    }

    @Override
    public TradeOrder cancel() {
        return this.toBuilder()
                .status(OrderStatus.CANCELLED)
                .build();
    }
}
