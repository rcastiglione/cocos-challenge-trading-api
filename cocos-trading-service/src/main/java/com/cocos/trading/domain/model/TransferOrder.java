package com.cocos.trading.domain.model;

import com.cocos.trading.domain.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public static final BigDecimal UNIT_CASH_PRICE = BigDecimal.ONE.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);

    public TransferOrder(
            Long id,
            Long instrumentId,
            Long userId,
            Integer size,
            BigDecimal price,
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
    public BigDecimal getCashImpact() {
        if (getSize() == null) {
            return BigDecimal.ZERO.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal unitPrice = getPrice() != null ? getPrice() : UNIT_CASH_PRICE;
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(getSize()))
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        return getSide() == OrderSide.CASH_IN ? total : total.negate();
    }

    @Override
    public TransferOrder cancel() {
        return this.toBuilder()
                .status(OrderStatus.CANCELLED)
                .build();
    }
}
