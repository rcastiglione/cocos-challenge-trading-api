package com.cocos.trading.domain.model;

import java.util.List;
import lombok.Builder;

/**
 * Domain read-model representing a user's complete portfolio valuation.
 *
 * @param user              The {@link User} owning the portfolio.
 * @param totalAccountValue Total account valuation in ARS ({@code availableCash + sum(positions.marketValue)}).
 * @param availableCash     Available ARS cash balance for trading.
 * @param positions         List of active equity positions.
 */
@Builder
public record Portfolio(
        User user,
        MonetaryAmount totalAccountValue,
        MonetaryAmount availableCash,
        List<AssetPosition> positions
) {
}
