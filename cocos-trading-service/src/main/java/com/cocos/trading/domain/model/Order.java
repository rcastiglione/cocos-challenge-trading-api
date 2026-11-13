package com.cocos.trading.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Domain aggregate root representing a trading order or cash transfer.
 *
 * @param id           Unique identifier of the order (may be null prior to persistence).
 * @param instrumentId Identifier of the traded instrument.
 * @param userId       Identifier of the user who placed the order.
 * @param size         Quantity of shares (or cash units for MONEDA).
 * @param price        Unit execution or limit price in ARS.
 * @param type         Execution type ({@link OrderType#MARKET} or {@link OrderType#LIMIT}).
 * @param side         Order side ({@link OrderSide#BUY}, {@link OrderSide#SELL}, {@link OrderSide#CASH_IN}, {@link OrderSide#CASH_OUT}).
 * @param status       Current lifecycle status of the order.
 * @param datetime     Timestamp when the order was created.
 */
public record Order(
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
    private static final int CURRENCY_SCALE = 2;

    /**
     * Calculates the maximum whole number of shares that can be traded for a given total ARS amount
     * and unit price (fractional shares are not permitted).
     *
     * @param amount    Total investment amount in ARS.
     * @param unitPrice Price per share in ARS.
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
     * Returns a copy of this order transitioned to {@link OrderStatus#CANCELLED}.
     *
     * @return A new {@link Order} instance with status {@link OrderStatus#CANCELLED}.
     */
    public Order cancel() {
        return new Order(id, instrumentId, userId, size, price, type, side, OrderStatus.CANCELLED, datetime);
    }
}

