package com.cocos.trading.api;

import com.cocos.trading.api.response.PortfolioApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Contract interface for portfolio valuation endpoints.
 */
@Tag(name = "Portfolio", description = "Operations related to user portfolio valuation and holdings")
@RequestMapping("/portfolios")
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
    @GetMapping("/{userId}")
    PortfolioApi getPortfolio(
            @Parameter(description = "User ID", example = "1", required = true)
            @PathVariable("userId") Long userId
    );
}

