package com.cocos.trading.domain.model;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;

/**
 * Domain read-model representing a user's complete portfolio valuation.
 *
 * @param userId            Identifier of the user.
 * @param accountNumber     Account number of the user.
 * @param totalAccountValue Total account valuation in ARS ({@code availableCash + sum(positions.marketValue)}).
 * @param availableCash     Available ARS cash balance for trading.
 * @param positions         List of active equity positions.
 */
@Builder
public record Portfolio(
        Long userId,
        String accountNumber,
        BigDecimal totalAccountValue,
        BigDecimal availableCash,
        List<AssetPosition> positions
) {
}

