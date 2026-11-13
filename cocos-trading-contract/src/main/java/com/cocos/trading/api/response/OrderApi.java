package com.cocos.trading.api.response;

import com.cocos.trading.api.common.OrderSideApi;
import com.cocos.trading.api.common.OrderStatusApi;
import com.cocos.trading.api.common.OrderTypeApi;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Builder;

/**
 * API representation of a trading order stored in the platform.
 *
 * @param id           Unique order identifier.
 * @param instrumentId Target instrument identifier.
 * @param userId       User identifier who placed the order.
 * @param size         Quantity of shares/units.
 * @param price        Execution or limit price in ARS.
 * @param type         Order execution type (MARKET, LIMIT).
 * @param side         Order side (BUY, SELL, CASH_IN, CASH_OUT).
 * @param status       Order status (NEW, FILLED, REJECTED, CANCELLED).
 * @param datetime     ISO-8601 formatted timestamp of the order.
 */
@Builder
@Schema(description = "Order execution details")
public record OrderApi(
        @Schema(description = "Order ID", example = "105")
        Long id,

        @Schema(description = "Instrument ID", example = "47")
        Long instrumentId,

        @Schema(description = "User ID", example = "1")
        Long userId,

        @Schema(description = "Quantity of shares or cash units", example = "10")
        Integer size,

        @Schema(description = "Execution or limit price in ARS", example = "925.85")
        BigDecimal price,

        @Schema(description = "Order type", example = "MARKET")
        OrderTypeApi type,

        @Schema(description = "Order side", example = "BUY")
        OrderSideApi side,

        @Schema(description = "Order status", example = "FILLED")
        OrderStatusApi status,

        @Schema(description = "Order creation timestamp", example = "2024-06-15T14:30:00")
        String datetime
) {
}

