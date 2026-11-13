package com.cocos.trading.application.port.in;

import com.cocos.trading.domain.model.Portfolio;

/**
 * Inbound port (use case) for retrieving a user's portfolio valuation.
 */
public interface GetPortfolioUseCase {

    /**
     * Calculates and retrieves the portfolio summary for the specified user.
     *
     * @param userId Unique identifier of the user.
     * @return The calculated {@link Portfolio}.
     */
    Portfolio getPortfolio(Long userId);
}

