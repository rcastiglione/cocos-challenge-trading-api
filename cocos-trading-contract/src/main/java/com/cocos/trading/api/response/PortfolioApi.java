package com.cocos.trading.api.response;

import com.cocos.trading.api.common.MonetaryAmount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;

/**
 * API response representing the complete valuation of a user's trading portfolio.
 *
 * @param user              Semantic summary of the user and trading account ({@code id}, {@code accountNumber}).
 * @param totalAccountValue Total account value in ARS ({@code availableCash + sum of positions' marketValue}).
 * @param availableCash     Available cash (ARS) for trading.
 * @param positions         List of active asset positions held by the user.
 */
@Builder
@Schema(description = "User portfolio valuation summary")
public record PortfolioApi(
        @Schema(description = "User identity and account details")
        UserSummaryApi user,

        @Schema(description = "Total account valuation in ARS")
        MonetaryAmount totalAccountValue,

        @Schema(description = "Available cash in ARS for trading")
        MonetaryAmount availableCash,

        @Schema(description = "List of held asset positions")
        List<AssetPositionApi> positions
) {
}
