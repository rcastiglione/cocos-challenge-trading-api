package com.cocos.trading.domain.model;

import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import java.math.BigDecimal;
import lombok.Builder;

/**
 * Value object representing an active equity position in a user's portfolio.
 *
 * @param instrument            The financial {@link InstrumentJpaEntity} held in the position.
 * @param quantity              Net quantity of shares held.
 * @param lastPrice             Latest market close price in ARS.
 * @param marketValue           Total monetary value of the position in ARS ({@code quantity * lastPrice}).
 * @param totalReturnPercentage Total return percentage relative to average cost basis.
 * @param dailyReturnPercentage Daily return percentage relative to previous close.
 */
@Builder
public record AssetPosition(
        InstrumentJpaEntity instrument,
        Long quantity,
        MonetaryAmount lastPrice,
        MonetaryAmount marketValue,
        BigDecimal totalReturnPercentage,
        BigDecimal dailyReturnPercentage
) {
}
