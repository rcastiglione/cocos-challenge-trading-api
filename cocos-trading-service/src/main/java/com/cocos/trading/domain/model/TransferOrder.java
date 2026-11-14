package com.cocos.trading.domain.model;

import com.cocos.trading.domain.exception.BusinessRuleViolationException;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Domain model representing a cash transfer movement ({@link OrderSide#CASH_IN} or {@link OrderSide#CASH_OUT})
 * on a currency instrument ({@code MONEDA}).
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
public final class TransferOrder extends Order {

    public static final MonetaryAmount UNIT_CASH_PRICE = MonetaryAmount.ONE_ARS;

    public TransferOrder(
            Long id,
            Long instrumentId,
            Long userId,
            Integer size,
            MonetaryAmount price,
            OrderType type,
            OrderSide side,
            OrderStatus status,
            LocalDateTime datetime
    ) {
        super(
                id,
                instrumentId,
                userId,
                size,
                price != null ? price : UNIT_CASH_PRICE,
                type != null ? type : OrderType.MARKET,
                validateTransferSide(side),
                status,
                datetime
        );
    }

    protected TransferOrder(TransferOrderBuilder<?, ?> builder) {
        super(builder);
        validateTransferSide(getSide());
    }

    private static OrderSide validateTransferSide(OrderSide side) {
        if (side == null || !side.isCashOperation()) {
            throw new BusinessRuleViolationException(
                    "TransferOrder requires a cash movement side (CASH_IN or CASH_OUT), but received: " + side
            );
        }
        return side;
    }

    @Override
    public MonetaryAmount getCashImpact() {
        if (getSize() == null) {
            return MonetaryAmount.ZERO_ARS;
        }
        MonetaryAmount unitPrice = getPrice() != null ? getPrice() : UNIT_CASH_PRICE;
        MonetaryAmount total = unitPrice.multiply(getSize());
        return getSide() == OrderSide.CASH_IN ? total : total.negate();
    }

    @Override
    public TransferOrder cancel() {
        return this.toBuilder()
                .status(OrderStatus.CANCELLED)
                .build();
    }
}
