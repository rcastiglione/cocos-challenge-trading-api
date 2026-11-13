package com.cocos.trading.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Filter criteria for searching market instruments by query (matching ticker and/or name),
 * or specifically by ticker and/or name.
 *
 * @param query  General search query matched against both ticker and name (case-insensitive).
 * @param ticker Optional ticker filter (case-insensitive substring match).
 * @param name   Optional instrument name filter (case-insensitive substring match).
 */
@Schema(description = "Search filter criteria for market instruments")
public record InstrumentSearchFilterRequestApi(
        @Schema(description = "General search query matching ticker or name", example = "PAMP")
        String query,
        @Schema(description = "Specific ticker filter", example = "PAMP")
        String ticker,
        @Schema(description = "Specific instrument name filter", example = "Pampa")
        String name
) {
}

