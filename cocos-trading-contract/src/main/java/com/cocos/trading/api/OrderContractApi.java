package com.cocos.trading.api;

import com.cocos.trading.api.request.OrderCreateRequestApi;
import com.cocos.trading.api.response.OrderApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Framework-agnostic contract interface for order submission and management.
 */
@Tag(name = "Orders", description = "Operations related to market/limit order routing and cancellation")
public interface OrderContractApi {

    /**
     * Submits a new order (MARKET or LIMIT; BUY, SELL, CASH_IN, or CASH_OUT) to the market.
     *
     * @param createRequest The order creation request payload.
     * @return The persisted {@link OrderApi} reflecting its resulting status (FILLED, NEW, or REJECTED).
     */
    @Operation(
            summary = "Submit an order to the market",
            description = "Places a MARKET or LIMIT order by share quantity (size) or total investment (amount). "
                    + "Insufficient funds or holdings result in a persisted REJECTED order."
    )
    OrderApi create(OrderCreateRequestApi createRequest);

    /**
     * Cancels an existing order if its current status is NEW.
     *
     * @param orderId The ID of the order to cancel.
     * @return The updated {@link OrderApi} with status CANCELLED.
     */
    @Operation(
            summary = "Cancel a pending order",
            description = "Cancels an order that is currently in NEW status"
    )
    OrderApi cancel(
            @Parameter(description = "Order ID to cancel", example = "105", required = true)
            Long orderId
    );
}
