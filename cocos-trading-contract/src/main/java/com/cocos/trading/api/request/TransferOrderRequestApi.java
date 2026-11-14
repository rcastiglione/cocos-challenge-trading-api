package com.cocos.trading.api.request;

import com.cocos.trading.api.common.MonetaryAmount;
import com.cocos.trading.api.common.OrderSideApi;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Polymorphic request payload for submitting a cash transfer order ({@link OrderSideApi#CASH_IN} or {@link OrderSideApi#CASH_OUT}).
 * <p>
 * Structurally requires only {@code userId}, {@code side}, and {@code amount} ({@link MonetaryAmount}).
 * The target currency instrument ({@code MONEDA}, e.g., {@code ARS}) is automatically resolved internally
 * from {@code amount.currency}, decoupling clients from database primary keys and market order fields.
 * </p>
 *
 * @param userId The unique identifier of the user initiating the transfer.
 * @param side   The cash transfer side (CASH_IN, CASH_OUT).
 * @param amount The direct monetary amount to transfer (its currency resolves the underlying MONEDA instrument).
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(description = "Request payload for submitting a cash transfer order (CASH_IN or CASH_OUT)")
public record TransferOrderRequestApi(
        @Schema(description = "User ID initiating the transfer", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long userId,

        @Schema(description = "Transfer side (CASH_IN, CASH_OUT)", example = "CASH_IN", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderSideApi side,

        @Schema(description = "Direct monetary amount to transfer (currency determines the underlying MONEDA instrument, e.g. ARS)", requiredMode = Schema.RequiredMode.REQUIRED)
        MonetaryAmount amount
) implements OrderCreateRequestApi {
}
