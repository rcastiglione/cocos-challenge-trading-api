package com.cocos.trading.api.request;

import com.cocos.trading.api.common.OrderSideApi;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Polymorphic request contract for submitting an order to the platform.
 * <p>
 * Discriminates based on the existing {@code side} property:
 * <ul>
 *   <li>{@link TradeOrderRequestApi} when {@code side} is {@code BUY} or {@code SELL}.</li>
 *   <li>{@link TransferOrderRequestApi} when {@code side} is {@code CASH_IN} or {@code CASH_OUT}.</li>
 * </ul>
 * Must be accompanied by a mandatory {@code Idempotency-Key} (UUID) HTTP header on {@code POST /orders}.
 * </p>
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "side",
        visible = true,
        defaultImpl = TradeOrderRequestApi.class
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TradeOrderRequestApi.class, names = {"BUY", "SELL"}),
        @JsonSubTypes.Type(value = TransferOrderRequestApi.class, names = {"CASH_IN", "CASH_OUT"})
})
@Schema(
        description = "Polymorphic request payload for submitting a trade order (BUY/SELL) or cash transfer order (CASH_IN/CASH_OUT)",
        discriminatorProperty = "side",
        subTypes = {TradeOrderRequestApi.class, TransferOrderRequestApi.class}
)
public sealed interface OrderCreateRequestApi
        permits TradeOrderRequestApi, TransferOrderRequestApi {

    Long userId();

    OrderSideApi side();
}
