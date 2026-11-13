package com.cocos.trading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * API representation of an asset position held within a user's portfolio.
 *
 * @param instrumentId          Unique identifier of the instrument.
 * @param ticker                Instrument ticker symbol.
 * @param name                  Full name of the instrument.
 * @param quantity              Total quantity of shares held.
 * @param lastPrice             Latest market close price in ARS.
 * @param marketValue           Total monetary value of the position in ARS (quantity * lastPrice).
 * @param totalReturnPercentage Total return percentage of the position relative to its average cost basis.
 * @param dailyReturnPercentage Daily return percentage based on latest close vs. previousClose.
 */
@Schema(description = "Asset position within a user's portfolio")
public record AssetPositionApi(
        @Schema(description = "Instrument ID", example = "47")
        Long instrumentId,

        @Schema(description = "Instrument ticker symbol", example = "MOLI")
        String ticker,

        @Schema(description = "Instrument description/name", example = "Molinos Río de la Plata")
        String name,

        @Schema(description = "Net quantity of shares held", example = "50")
        Integer quantity,

        @Schema(description = "Latest market close price in ARS", example = "925.85")
        BigDecimal lastPrice,

        @Schema(description = "Total monetary valuation of the position in ARS", example = "46292.50")
        BigDecimal marketValue,

        @Schema(description = "Total return percentage (%)", example = "12.45")
        BigDecimal totalReturnPercentage,

        @Schema(description = "Daily return percentage (%)", example = "2.15")
        BigDecimal dailyReturnPercentage
) {
}

