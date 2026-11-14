package com.cocos.trading.api.response;

import com.cocos.trading.api.common.MonetaryAmount;
import com.cocos.trading.api.common.OrderSideApi;
import com.cocos.trading.api.common.OrderStatusApi;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Polymorphic API representation of a cash transfer order ({@link OrderSideApi#CASH_IN} or {@link OrderSideApi#CASH_OUT}).
 * Exposes exclusively the direct monetary {@code amount} transferred, without market execution or equity instrument fields
 * ({@code instrument}, {@code type}, {@code quantity}, or unit {@code price}).
 *
 * @param id       Unique order identifier.
 * @param user     Semantic summary of the user identity and trading account.
 * @param amount   Total cash transfer amount in ARS.
 * @param side     Transfer order side (CASH_IN, CASH_OUT).
 * @param status   Transfer execution status (always FILLED for completed cash transfers).
 * @param datetime ISO-8601 formatted timestamp of the transfer.
 */
@Builder
@Schema(description = "Executed cash transfer order details (CASH_IN or CASH_OUT)")
public record TransferOrderApi(
        @Schema(description = "Order ID", example = "15")
        Long id,

        @Schema(description = "User identity and trading account summary")
        UserSummaryApi user,

        @Schema(description = "Total cash transfer amount in ARS")
        MonetaryAmount amount,

        @Schema(description = "Order side", example = "CASH_IN")
        OrderSideApi side,

        @Schema(description = "Transfer execution status (always FILLED for completed cash transfers)", example = "FILLED")
        OrderStatusApi status,

        @Schema(description = "Order creation timestamp", example = "2024-06-15T14:35:00")
        String datetime
) implements OrderApi {
}
