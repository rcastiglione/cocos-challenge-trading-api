package com.cocos.trading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Semantic summary of static financial instrument identity embedded inside portfolio positions and order responses.
 *
 * @param id     Unique identifier of the instrument.
 * @param ticker Ticker symbol of the instrument (e.g., {@code "PAMP"}).
 */
@Builder
@Schema(description = "Static financial instrument identity summary")
public record InstrumentSummaryApi(
        @Schema(description = "Instrument ID", example = "47")
        Long id,

        @Schema(description = "Instrument ticker symbol", example = "PAMP")
        String ticker
) {
}
