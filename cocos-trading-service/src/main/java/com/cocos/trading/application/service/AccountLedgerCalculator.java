package com.cocos.trading.application.service;

import com.cocos.trading.domain.model.Order;
import com.cocos.trading.domain.model.OrderSide;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Pure domain calculation utility for deriving a user's available cash balance and share holdings
 * from a chronological list of {@code FILLED} orders.
 */
public final class AccountLedgerCalculator {

    private static final int CURRENCY_SCALE = 2;

    private AccountLedgerCalculator() {
    }

    /**
     * Calculates the net available cash in ARS from all {@code FILLED} orders of a user.
     *<ul>
     *   <li>{@link OrderSide#CASH_IN}: adds {@code size * price} (or {@code size} if price is null/1).</li>
     *   <li>{@link OrderSide#CASH_OUT}: subtracts {@code size * price} (or {@code size} if price is null/1).</li>
     *   <li>{@link OrderSide#BUY}: subtracts {@code size * price}.</li>
     *   <li>{@link OrderSide#SELL}: adds {@code size * price}.</li>
     *</ul>
     *
     * @param filledOrders Chronological list of {@code FILLED} orders.
     * @return Net available cash in ARS rounded to 2 decimal places.
     */
    public static BigDecimal calculateAvailableCash(List<Order> filledOrders) {
        BigDecimal cash = BigDecimal.ZERO;
        if (filledOrders == null) {
            return cash.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        }

        for (Order order : filledOrders) {
            if (order.size() == null || order.side() == null) {
                continue;
            }
            BigDecimal unitPrice = order.price() != null ? order.price() : BigDecimal.ONE;
            BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(order.size()));

            switch (order.side()) {
                case CASH_IN, SELL -> cash = cash.add(amount);
                case CASH_OUT, BUY -> cash = cash.subtract(amount);
            }
        }
        return cash.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the net quantity of shares currently held by a user for a specific instrument
     * from all {@code FILLED} orders.
     *
     * @param filledOrders Chronological list of {@code FILLED} orders.
     * @param instrumentId The target instrument ID.
     * @return Net share quantity held.
     */
    public static int calculateAvailableShares(List<Order> filledOrders, Long instrumentId) {
        if (filledOrders == null || instrumentId == null) {
            return 0;
        }
        int shares = 0;
        for (Order order : filledOrders) {
            if (!instrumentId.equals(order.instrumentId()) || order.size() == null || order.side() == null) {
                continue;
            }
            if (order.side() == OrderSide.BUY) {
                shares += order.size();
            } else if (order.side() == OrderSide.SELL) {
                shares -= order.size();
            }
        }
        return shares;
    }
}

