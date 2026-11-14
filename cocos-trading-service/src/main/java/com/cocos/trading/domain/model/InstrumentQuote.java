package com.cocos.trading.domain.model;

import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import java.math.BigDecimal;
import lombok.Builder;

/**
 * Projection representing an instrument enriched with its latest market quote.
 *
 * @param instrument            The core {@link InstrumentJpaEntity}.
 * @param lastPrice             Latest market close price (nullable if no market data exists).
 * @param previousClose         Previous market close price (nullable if no market data exists).
 * @param dailyReturnPercentage Daily return percentage (nullable if no market data exists).
 */
@Builder
public record InstrumentQuote(
        InstrumentJpaEntity instrument,
        MonetaryAmount lastPrice,
        MonetaryAmount previousClose,
        BigDecimal dailyReturnPercentage
) {
}
