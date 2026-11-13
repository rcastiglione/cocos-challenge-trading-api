package com.cocos.trading.application.service;

import com.cocos.trading.domain.model.Order;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.TradeOrder;
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
     * Calculates the net available cash in ARS from all {@code FILLED} orders of a user
     * by summing each order's polymorphic {@link Order#getCashImpact()}.
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
            if (order.getSize() == null || order.getSide() == null) {
                continue;
            }
            cash = cash.add(order.getCashImpact());
        }
        return cash.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the net quantity of shares currently held by a user for a specific instrument
     * from all {@code FILLED} {@link TradeOrder} instances.
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
            if (!(order instanceof TradeOrder tradeOrder)) {
                continue;
            }
            if (!instrumentId.equals(tradeOrder.getInstrumentId()) || tradeOrder.getSize() == null) {
                continue;
            }
            if (tradeOrder.getSide() == OrderSide.BUY) {
                shares += tradeOrder.getSize();
            } else if (tradeOrder.getSide() == OrderSide.SELL) {
                shares -= tradeOrder.getSize();
            }
        }
        return shares;
    }
}
