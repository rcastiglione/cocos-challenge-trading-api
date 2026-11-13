package com.cocos.trading.domain.model;

import java.math.BigDecimal;

/**
 * Domain projection representing an instrument enriched with its latest market quote.
 *
 * @param instrument            The core {@link Instrument}.
 * @param lastPrice             Latest market close price (nullable if no market data exists).
 * @param previousClose         Previous market close price (nullable if no market data exists).
 * @param dailyReturnPercentage Daily return percentage (nullable if no market data exists).
 */
public record InstrumentQuote(
        Instrument instrument,
        BigDecimal lastPrice,
        BigDecimal previousClose,
        BigDecimal dailyReturnPercentage
) {
}

