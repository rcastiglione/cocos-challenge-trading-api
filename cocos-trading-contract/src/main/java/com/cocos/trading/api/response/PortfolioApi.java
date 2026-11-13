package com.cocos.trading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * API response representing the complete valuation of a user's trading portfolio.
 *
 * @param userId            The ID of the user.
 * @param accountNumber     The account number of the user.
 * @param totalAccountValue Total account value in ARS (availableCash + sum of positions' marketValue).
 * @param availableCash     Available cash (ARS) for trading.
 * @param positions         List of active asset positions held by the user.
 */
@Schema(description = "User portfolio valuation summary")
public record PortfolioApi(
        @Schema(description = "User ID", example = "1")
        Long userId,

        @Schema(description = "User account number", example = "10001")
        String accountNumber,

        @Schema(description = "Total account valuation in ARS", example = "815420.50")
        BigDecimal totalAccountValue,

        @Schema(description = "Available cash in ARS for trading", example = "769128.00")
        BigDecimal availableCash,

        @Schema(description = "List of held asset positions")
        List<AssetPositionApi> positions
) {
}

