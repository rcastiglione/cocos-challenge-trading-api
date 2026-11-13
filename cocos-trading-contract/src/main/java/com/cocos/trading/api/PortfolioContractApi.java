package com.cocos.trading.api;

import com.cocos.trading.api.response.PortfolioApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Framework-agnostic contract interface for portfolio valuation endpoints.
 */
@Tag(name = "Portfolio", description = "Operations related to user portfolio valuation and holdings")
public interface PortfolioContractApi {

    /**
     * Retrieves the complete portfolio summary for a given user, including total account value,
     * available ARS cash, and individual asset positions with their market value and total return.
     *
     * @param userId The ID of the user.
     * @return The calculated {@link PortfolioApi}.
     */
    @Operation(
            summary = "Get user portfolio",
            description = "Returns the total account value, available ARS cash, and held asset positions for a user"
    )
    PortfolioApi getPortfolio(
            @Parameter(description = "User ID", example = "1", required = true)
            Long userId
    );
}
