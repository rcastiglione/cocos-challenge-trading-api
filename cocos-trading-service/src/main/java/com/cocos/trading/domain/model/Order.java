package com.cocos.trading.domain.model;

import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Abstract domain aggregate root representing an order or cash movement in the trading ledger.
 * Concrete subclasses isolate market equity trades ({@link TradeOrder}) from currency transfers ({@link TransferOrder}).
 */
@Getter
@ToString
@EqualsAndHashCode
@SuperBuilder(toBuilder = true)
public abstract class Order {

    protected static final int CURRENCY_SCALE = MonetaryAmount.CURRENCY_SCALE;

    private final Long id;
    private final Long instrumentId;
    private final Long userId;
    private final Integer size;
    private final MonetaryAmount price;
    private final OrderType type;
    private final OrderSide side;
    private final OrderStatus status;
    private final LocalDateTime datetime;

    protected Order(
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
        this.id = id;
        this.instrumentId = instrumentId;
        this.userId = userId;
        this.size = size;
        this.price = price;
        this.type = type;
        this.side = side;
        this.status = status;
        this.datetime = datetime;
    }

    /**
     * Calculates the maximum whole number of shares/units that can be traded for a given total investment amount
     * and unit price (fractional shares are not permitted).
     *
     * @param amount    Total investment amount as a {@link MonetaryAmount}.
     * @param unitPrice Price per share/unit as a {@link MonetaryAmount}.
     * @return Maximum integer number of shares ({@code floor(amount / unitPrice)}).
     */
    public static int calculateMaxShareSize(MonetaryAmount amount, MonetaryAmount unitPrice) {
        if (amount == null || unitPrice == null) {
            return 0;
        }
        return amount.divideToIntegralShares(unitPrice);
    }

    /**
     * Calculates the total monetary value of the order ({@code size * price}).
     *
     * @return Total order amount as a {@link MonetaryAmount}.
     */
    public MonetaryAmount getTotalAmount() {
        if (size == null || price == null) {
            return MonetaryAmount.ZERO_ARS;
        }
        return price.multiply(size);
    }

    /**
     * Calculates the signed cash balance impact of this order when {@link OrderStatus#FILLED}.
     *
     * @return Signed cash delta as a {@link MonetaryAmount} ({@code +} for inflows/sales, {@code -} for outflows/purchases).
     */
    public abstract MonetaryAmount getCashImpact();

    /**
     * Returns a copy of this order transitioned to {@link OrderStatus#CANCELLED}.
     *
     * @return A new {@link Order} instance of the same concrete subclass with status {@link OrderStatus#CANCELLED}.
     */
    public abstract Order cancel();
}
