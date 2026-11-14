package com.cocos.trading.domain.model;

import com.cocos.trading.repository.entity.UserJpaEntity;
import java.util.List;
import lombok.Builder;

/**
 * Read-model representing a user's complete portfolio valuation.
 *
 * @param user              The {@link UserJpaEntity} owning the portfolio.
 * @param totalAccountValue Total account valuation in ARS ({@code availableCash + sum(positions.marketValue)}).
 * @param availableCash     Available ARS cash balance for trading.
 * @param positions         List of active equity positions.
 */
@Builder
public record Portfolio(
        UserJpaEntity user,
        MonetaryAmount totalAccountValue,
        MonetaryAmount availableCash,
        List<AssetPosition> positions
) {
}
