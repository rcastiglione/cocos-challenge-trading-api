package com.cocos.trading.domain.model;

import java.math.BigDecimal;

/**
 * Domain value object representing an active equity position in a user's portfolio.
 *
 * @param instrumentId          Unique identifier of the instrument.
 * @param ticker                Ticker symbol of the instrument.
 * @param name                  Name of the instrument.
 * @param quantity              Net quantity of shares held.
 * @param lastPrice             Latest market close price in ARS.
 * @param marketValue           Total monetary value of the position in ARS ({@code quantity * lastPrice}).
 * @param totalReturnPercentage Total return percentage relative to average cost basis.
 * @param dailyReturnPercentage Daily return percentage relative to previous close.
 */
public record AssetPosition(
        Long instrumentId,
        String ticker,
        String name,
        Integer quantity,
        BigDecimal lastPrice,
        BigDecimal marketValue,
        BigDecimal totalReturnPercentage,
        BigDecimal dailyReturnPercentage
) {
}

