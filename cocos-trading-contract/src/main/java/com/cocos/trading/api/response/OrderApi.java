package com.cocos.trading.api.response;

import com.cocos.trading.api.common.OrderSideApi;
import com.cocos.trading.api.common.OrderStatusApi;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Polymorphic API representation of an order stored in the platform.
 * <p>
 * Discriminates based on the existing {@code side} property:
 * <ul>
 *   <li>{@link TradeOrderApi} when {@code side} is {@code BUY} or {@code SELL} (exposes {@code quantity}, {@code price}, and {@code type}).</li>
 *   <li>{@link TransferOrderApi} when {@code side} is {@code CASH_IN} or {@code CASH_OUT} (exposes {@code amount}).</li>
 * </ul>
 * </p>
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "side",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TradeOrderApi.class, names = {"BUY", "SELL"}),
        @JsonSubTypes.Type(value = TransferOrderApi.class, names = {"CASH_IN", "CASH_OUT"})
})
@Schema(
        description = "Polymorphic order execution details (TradeOrderApi for BUY/SELL, TransferOrderApi for CASH_IN/CASH_OUT)",
        discriminatorProperty = "side",
        subTypes = {TradeOrderApi.class, TransferOrderApi.class}
)
public sealed interface OrderApi
        permits TradeOrderApi, TransferOrderApi {

    Long id();

    UserSummaryApi user();

    OrderSideApi side();

    OrderStatusApi status();

    String datetime();
}
