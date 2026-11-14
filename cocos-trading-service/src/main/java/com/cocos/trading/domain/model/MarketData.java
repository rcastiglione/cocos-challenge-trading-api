package com.cocos.trading.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.Builder;

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
@Builder
public record MarketData(
        Long id,
        Long instrumentId,
        MonetaryAmount high,
        MonetaryAmount low,
        MonetaryAmount open,
        MonetaryAmount close,
        MonetaryAmount previousClose,
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
        if (close == null || previousClose == null || previousClose.isZeroOrNegative()) {
            return BigDecimal.ZERO.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
        }
        return close.amount().subtract(previousClose.amount())
                .divide(previousClose.amount(), CALCULATION_SCALE, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }
}
