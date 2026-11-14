package com.cocos.trading.api.request;

import com.cocos.trading.api.common.MonetaryAmount;
import com.cocos.trading.api.common.OrderSideApi;
import com.cocos.trading.api.common.OrderTypeApi;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Request payload for submitting a new order to the market.
 * Supports sizing either by exact share quantity ({@code quantity}) or by total ARS investment ({@code amount}).
 * Must be accompanied by a mandatory {@code Idempotency-Key} (UUID) HTTP header on {@code POST /orders}.
 *
 * @param userId       The unique identifier of the user placing the order.
 * @param instrumentId The unique identifier of the target financial instrument.
 * @param side         The order side (BUY, SELL, CASH_IN, CASH_OUT).
 * @param type         The order execution type (MARKET, LIMIT).
 * @param quantity     Optional exact number of shares/units to trade (mutually exclusive with amount).
 * @param amount       Optional total investment amount in ARS (used to calculate maximum whole shares).
 * @param price        Target execution price in ARS (required for LIMIT orders; ignored for MARKET orders).
 */
@Builder
@Schema(description = "Request payload for submitting a market or limit order (requires 'Idempotency-Key' UUID header)")
public record OrderCreateRequestApi(
        @Schema(description = "User ID placing the order", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long userId,

        @Schema(description = "Instrument ID to trade", example = "47", requiredMode = Schema.RequiredMode.REQUIRED)
        Long instrumentId,

        @Schema(description = "Order side (BUY, SELL, CASH_IN, CASH_OUT)", example = "BUY", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderSideApi side,

        @Schema(description = "Order execution type (MARKET, LIMIT)", example = "MARKET", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderTypeApi type,

        @Schema(description = "Exact quantity of shares (or ARS units for MONEDA) to trade", example = "10")
        Integer quantity,

        @Schema(description = "Total investment amount in ARS (automatically calculates maximum whole shares)")
        MonetaryAmount amount,

        @Schema(description = "Limit price in ARS (required for LIMIT orders)")
        MonetaryAmount price
) {
}
