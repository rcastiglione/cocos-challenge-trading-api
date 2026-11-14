package com.cocos.trading.application.service;

import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.repository.entity.MarketDataJpaEntity;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import com.cocos.trading.repository.entity.TradeOrderJpaEntity;
import com.cocos.trading.repository.entity.TransferOrderJpaEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Application calculation utility for deriving order totals, cash balance impacts, share quantity sizing,
 * available cash balances, net share holdings, and daily return percentages directly from polymorphic JPA entities.
 */
public final class AccountLedgerCalculator {

    public static final MonetaryAmount UNIT_CASH_PRICE = MonetaryAmount.ONE_ARS;

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int CALCULATION_SCALE = 6;
    private static final int DISPLAY_SCALE = 2;

    private AccountLedgerCalculator() {
    }

    /**
     * Calculates the maximum whole number of shares/units that can be traded for a given total investment amount
     * and unit price (fractional shares are not permitted).
     *
     * @param amount    Total investment amount as a {@link MonetaryAmount}.
     * @param unitPrice Price per share/unit as a {@link MonetaryAmount}.
     * @return Maximum integer quantity of shares ({@code floor(amount / unitPrice)}).
     */
    public static int calculateMaxShareQuantity(MonetaryAmount amount, MonetaryAmount unitPrice) {
        if (amount == null || unitPrice == null) {
            return 0;
        }
        return amount.divideToIntegralShares(unitPrice);
    }

    /**
     * Calculates the total monetary value of an order ({@code quantity * price}).
     *
     * @param order The order entity.
     * @return Total order amount as a {@link MonetaryAmount}.
     */
    public static MonetaryAmount calculateTotalAmount(OrderJpaEntity order) {
        if (order == null || order.getQuantity() == null) {
            return MonetaryAmount.ZERO_ARS;
        }
        MonetaryAmount unitPrice = order.getPrice() != null
                ? MonetaryAmount.ars(order.getPrice())
                : (order instanceof TransferOrderJpaEntity ? UNIT_CASH_PRICE : null);
        if (unitPrice == null) {
            return MonetaryAmount.ZERO_ARS;
        }
        return unitPrice.multiply(order.getQuantity());
    }

    /**
     * Calculates the signed cash balance impact of a polymorphic order ({@link TradeOrderJpaEntity} or
     * {@link TransferOrderJpaEntity}) when {@code FILLED}.
     *
     * @param order The order entity.
     * @return Signed cash delta as a {@link MonetaryAmount} ({@code +} for inflows/sales, {@code -} for outflows/purchases).
     */
    public static MonetaryAmount calculateCashImpact(OrderJpaEntity order) {
        if (order == null || order.getQuantity() == null || order.getSide() == null) {
            return MonetaryAmount.ZERO_ARS;
        }
        return switch (order) {
            case TradeOrderJpaEntity tradeOrder -> {
                if (tradeOrder.getPrice() == null) {
                    yield MonetaryAmount.ZERO_ARS;
                }
                MonetaryAmount total = calculateTotalAmount(tradeOrder);
                yield tradeOrder.getSide() == OrderSide.SELL ? total : total.negate();
            }
            case TransferOrderJpaEntity transferOrder -> {
                MonetaryAmount total = calculateTotalAmount(transferOrder);
                yield transferOrder.getSide() == OrderSide.CASH_IN ? total : total.negate();
            }
            default -> MonetaryAmount.ZERO_ARS;
        };
    }

    /**
     * Calculates the net available cash in ARS from all {@code FILLED} orders of a user
     * by summing each order's cash impact via {@link #calculateCashImpact(OrderJpaEntity)}.
     *
     * @param filledOrders Chronological list of {@code FILLED} orders.
     * @return Net available cash as an {@code ARS} {@link MonetaryAmount}.
     */
    public static MonetaryAmount calculateAvailableCash(List<OrderJpaEntity> filledOrders) {
        MonetaryAmount cash = MonetaryAmount.ZERO_ARS;
        if (filledOrders == null) {
            return cash;
        }

        for (OrderJpaEntity order : filledOrders) {
            if (order.getQuantity() == null || order.getSide() == null) {
                continue;
            }
            cash = cash.add(calculateCashImpact(order));
        }
        return cash;
    }

    /**
     * Calculates the net quantity of shares currently held by a user for a specific instrument
     * from all {@code FILLED} {@link TradeOrderJpaEntity} instances.
     *
     * @param filledOrders Chronological list of {@code FILLED} orders.
     * @param instrumentId The target instrument ID.
     * @return Net share quantity held.
     */
    public static int calculateAvailableShares(List<OrderJpaEntity> filledOrders, Long instrumentId) {
        if (filledOrders == null || instrumentId == null) {
            return 0;
        }
        int shares = 0;
        for (OrderJpaEntity order : filledOrders) {
            if (!(order instanceof TradeOrderJpaEntity tradeOrder)) {
                continue;
            }
            if (tradeOrder.getInstrument() == null
                    || !instrumentId.equals(tradeOrder.getInstrument().getId())
                    || tradeOrder.getQuantity() == null) {
                continue;
            }
            if (tradeOrder.getSide() == OrderSide.BUY) {
                shares += tradeOrder.getQuantity();
            } else if (tradeOrder.getSide() == OrderSide.SELL) {
                shares -= tradeOrder.getQuantity();
            }
        }
        return shares;
    }

    /**
     * Calculates the daily return percentage using {@code close} and {@code previousClose} from {@link MarketDataJpaEntity}:
     * {@code ((close - previousClose) / previousClose) * 100}.
     *
     * @param marketData The market data entry.
     * @return Daily return percentage rounded to 2 decimal places, or {@code 0.00} if prices are missing/zero.
     */
    public static BigDecimal calculateDailyReturnPercentage(MarketDataJpaEntity marketData) {
        if (marketData == null
                || marketData.getClose() == null
                || marketData.getPreviousClose() == null
                || marketData.getPreviousClose().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
        }
        return marketData.getClose().subtract(marketData.getPreviousClose())
                .divide(marketData.getPreviousClose(), CALCULATION_SCALE, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }
}
