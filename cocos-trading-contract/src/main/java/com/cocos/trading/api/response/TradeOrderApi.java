package com.cocos.trading.api.response;

import com.cocos.trading.api.common.MonetaryAmount;
import com.cocos.trading.api.common.OrderSideApi;
import com.cocos.trading.api.common.OrderStatusApi;
import com.cocos.trading.api.common.OrderTypeApi;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Polymorphic API representation of an equity trade order ({@link OrderSideApi#BUY} or {@link OrderSideApi#SELL}).
 * Exposes the executed/requested share {@code quantity} and unit {@code price} in ARS.
 *
 * @param id         Unique order identifier.
 * @param user       Semantic summary of the user identity and trading account.
 * @param instrument Semantic summary of the target equity instrument.
 * @param quantity   Quantity of whole shares traded.
 * @param price      Execution or limit unit price in ARS.
 * @param type       Order execution type (MARKET, LIMIT).
 * @param side       Trade order side (BUY, SELL).
 * @param status     Order status (NEW, FILLED, REJECTED, CANCELLED).
 * @param datetime   ISO-8601 formatted timestamp of the order.
 */
@Builder
@Schema(description = "Executed or pending equity trade order details (BUY or SELL)")
public record TradeOrderApi(
        @Schema(description = "Order ID", example = "105")
        Long id,

        @Schema(description = "User identity and trading account summary")
        UserSummaryApi user,

        @Schema(description = "Target equity instrument summary")
        InstrumentSummaryApi instrument,

        @Schema(description = "Quantity of whole shares traded", example = "10")
        Long quantity,

        @Schema(description = "Execution or limit unit price in ARS")
        MonetaryAmount price,

        @Schema(description = "Order type", example = "MARKET")
        OrderTypeApi type,

        @Schema(description = "Order side", example = "BUY")
        OrderSideApi side,

        @Schema(description = "Order status", example = "FILLED")
        OrderStatusApi status,

        @Schema(description = "Order creation timestamp", example = "2024-06-15T14:30:00")
        String datetime
) implements OrderApi {
}
