package com.cocos.trading.api.response;

import com.cocos.trading.api.common.MonetaryAmount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Builder;

/**
 * API representation of an asset position held within a user's portfolio.
 *
 * @param instrument            Static identity of the financial instrument ({@code id}, {@code ticker}).
 * @param quantity              Total quantity of shares held.
 * @param lastPrice             Latest market close price in ARS.
 * @param marketValue           Total monetary value of the position in ARS ({@code quantity * lastPrice}).
 * @param totalReturnPercentage Total return percentage of the position relative to its average cost basis.
 * @param dailyReturnPercentage Daily return percentage based on latest close vs. previousClose.
 */
@Builder
@Schema(description = "Asset position within a user's portfolio")
public record AssetPositionApi(
        @Schema(description = "Financial instrument metadata")
        InstrumentSummaryApi instrument,

        @Schema(description = "Net quantity of shares held", example = "298")
        Integer quantity,

        @Schema(description = "Latest market close price in ARS")
        MonetaryAmount lastPrice,

        @Schema(description = "Total monetary valuation of the position in ARS")
        MonetaryAmount marketValue,

        @Schema(description = "Total return percentage (%)", example = "2.15")
        BigDecimal totalReturnPercentage,

        @Schema(description = "Daily return percentage (%)", example = "1.55")
        BigDecimal dailyReturnPercentage
) {
}
