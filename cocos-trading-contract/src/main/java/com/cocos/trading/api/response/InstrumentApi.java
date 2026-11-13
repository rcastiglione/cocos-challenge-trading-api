package com.cocos.trading.api.response;

import com.cocos.trading.api.common.InstrumentTypeApi;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * API representation of a tradable market instrument enriched with latest market data when available.
 *
 * @param id                    Unique identifier of the instrument.
 * @param ticker                Ticker symbol of the instrument.
 * @param name                  Full name of the instrument.
 * @param type                  Type of the instrument (ACCIONES, MONEDA).
 * @param lastPrice             Latest close price in ARS (if market data is available).
 * @param previousClose         Previous close price in ARS (if market data is available).
 * @param dailyReturnPercentage Daily return percentage (if market data is available).
 */
@Schema(description = "Financial instrument details")
public record InstrumentApi(
        @Schema(description = "Instrument ID", example = "47")
        Long id,

        @Schema(description = "Ticker symbol", example = "MOLI")
        String ticker,

        @Schema(description = "Instrument name", example = "Molinos Río de la Plata")
        String name,

        @Schema(description = "Instrument type", example = "ACCIONES")
        InstrumentTypeApi type,

        @Schema(description = "Latest market close price in ARS", example = "925.85")
        BigDecimal lastPrice,

        @Schema(description = "Previous market close price in ARS", example = "906.35")
        BigDecimal previousClose,

        @Schema(description = "Daily return percentage (%)", example = "2.15")
        BigDecimal dailyReturnPercentage
) {
}

