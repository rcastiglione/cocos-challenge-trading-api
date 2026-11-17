package com.cocos.trading.api.request;

import com.cocos.trading.api.common.MonetaryAmount;
import com.cocos.trading.api.common.OrderSideApi;
import com.cocos.trading.api.common.OrderTypeApi;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Polymorphic request payload for submitting a market or limit equity trade order ({@link OrderSideApi#BUY} or {@link OrderSideApi#SELL}).
 * Supports sizing either by exact share quantity ({@code quantity}) or by total ARS investment ({@code amount}).
 *
 * @param userId       The unique identifier of the user placing the order.
 * @param instrumentId The unique identifier of the target equity instrument.
 * @param side         The trade order side (BUY, SELL).
 * @param type         The order execution type (MARKET, LIMIT).
 * @param quantity     Exact number of whole shares to trade (mutually exclusive with amount).
 * @param amount       Total investment amount in ARS used to calculate maximum whole shares (mutually exclusive with quantity).
 * @param price        Target limit unit price in ARS (required for LIMIT orders; ignored for MARKET orders).
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(description = "Request payload for submitting an equity trade order (BUY or SELL)")
public record TradeOrderRequestApi(
        @Schema(description = "User ID placing the order", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long userId,

        @Schema(description = "Equity instrument ID to trade", example = "47", requiredMode = Schema.RequiredMode.REQUIRED)
        Long instrumentId,

        @Schema(description = "Trade order side (BUY, SELL)", example = "BUY", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderSideApi side,

        @Schema(description = "Order execution type (MARKET, LIMIT)", example = "MARKET", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderTypeApi type,

        @Schema(description = "Exact quantity of shares to trade (mutually exclusive with amount)", example = "10")
        Long quantity,

        @Schema(description = "Total investment amount in ARS (automatically calculates maximum whole shares; mutually exclusive with quantity)")
        MonetaryAmount amount,

        @Schema(description = "Limit unit price in ARS (required for LIMIT orders)")
        MonetaryAmount price
) implements OrderCreateRequestApi {
}
