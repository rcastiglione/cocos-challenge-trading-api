package com.cocos.trading.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Domain entity representing daily market price data for a specific instrument.
 *
 * @param id            Unique identifier of the market data entry.
 * @param instrumentId  Identifier of the associated instrument.
 * @param high          Daily high price in ARS.
 * @param low           Daily low price in ARS.
 * @param open          Daily opening price in ARS.
 * @param close         Latest closing price in ARS.
 * @param previousClose Previous day's closing price in ARS.
 * @param date          Market session date.
 */
public record MarketData(
        Long id,
        Long instrumentId,
        BigDecimal high,
        BigDecimal low,
        BigDecimal open,
        BigDecimal close,
        BigDecimal previousClose,
        LocalDate date
) {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int CALCULATION_SCALE = 6;
    private static final int DISPLAY_SCALE = 2;

    /**
     * Calculates the daily return percentage using {@code close} and {@code previousClose}:
     * {@code ((close - previousClose) / previousClose) * 100}.
     *
     * @return Daily return percentage rounded to 2 decimal places, or {@code 0.00} if prices are missing/zero.
     */
    public BigDecimal calculateDailyReturnPercentage() {
        if (close == null || previousClose == null || previousClose.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
        }
        return close.subtract(previousClose)
                .divide(previousClose, CALCULATION_SCALE, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }
}

