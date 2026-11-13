package com.cocos.trading.api;

import com.cocos.trading.api.request.OrderCreateRequestApi;
import com.cocos.trading.api.response.OrderApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Framework-agnostic contract interface for order submission and management.
 */
@Tag(
        name = "Orders",
        description = "Operations related to market/limit equity orders (BUY, SELL), cash transfers (CASH_IN, CASH_OUT), and order cancellation"
)
public interface OrderContractApi {

    /**
     * Submits a new order (MARKET or LIMIT; BUY, SELL, CASH_IN, or CASH_OUT) to the market.
     *
     * @param createRequest The order creation request payload.
     * @return The persisted {@link OrderApi} reflecting its resulting status (FILLED, NEW, or REJECTED).
     */
    @Operation(
            summary = "Submit an equity order (BUY/SELL) or cash transfer (CASH_IN/CASH_OUT)",
            description = "Places a MARKET or LIMIT equity order (BUY, SELL) or a cash transfer (CASH_IN, CASH_OUT on MONEDA instruments) "
                    + "by exact quantity (size) or total ARS amount (amount). Equity orders with insufficient funds/holdings result in a persisted "
                    + "REJECTED order, while CASH_OUT transfers exceeding availableCash are rejected with a 409 BusinessRuleViolationProblem."
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
