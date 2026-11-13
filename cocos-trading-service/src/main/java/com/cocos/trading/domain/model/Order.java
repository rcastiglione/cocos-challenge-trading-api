package com.cocos.trading.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    protected static final int CURRENCY_SCALE = 2;

    private final Long id;
    private final Long instrumentId;
    private final Long userId;
    private final Integer size;
    private final BigDecimal price;
    private final OrderType type;
    private final OrderSide side;
    private final OrderStatus status;
    private final LocalDateTime datetime;

    protected Order(
            Long id,
            Long instrumentId,
            Long userId,
            Integer size,
            BigDecimal price,
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
     * Calculates the maximum whole number of shares/units that can be traded for a given total ARS amount
     * and unit price (fractional shares are not permitted).
     *
     * @param amount    Total investment amount in ARS.
     * @param unitPrice Price per share/unit in ARS.
     * @return Maximum integer number of shares ({@code floor(amount / unitPrice)}).
     */
    public static int calculateMaxShareSize(BigDecimal amount, BigDecimal unitPrice) {
        if (amount == null || unitPrice == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return amount.divideToIntegralValue(unitPrice).intValueExact();
    }

    /**
     * Calculates the total monetary value of the order ({@code size * price}).
     *
     * @return Total order amount in ARS rounded to 2 decimal places.
     */
    public BigDecimal getTotalAmount() {
        if (size == null || price == null) {
            return BigDecimal.ZERO.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        }
        return price.multiply(BigDecimal.valueOf(size)).setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the signed ARS cash balance impact of this order when {@link OrderStatus#FILLED}.
     *
     * @return Signed cash delta in ARS ({@code +} for inflows/sales, {@code -} for outflows/purchases).
     */
    public abstract BigDecimal getCashImpact();

    /**
     * Returns a copy of this order transitioned to {@link OrderStatus#CANCELLED}.
     *
     * @return A new {@link Order} instance of the same concrete subclass with status {@link OrderStatus#CANCELLED}.
     */
    public abstract Order cancel();
}
