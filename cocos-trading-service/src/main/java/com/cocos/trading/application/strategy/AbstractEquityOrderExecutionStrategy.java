package com.cocos.trading.application.strategy;

import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.domain.exception.BusinessRuleViolationException;
import com.cocos.trading.domain.model.Order;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.TradeOrder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Abstract base class for equity ({@code ACCIONES}) order execution strategies,
 * providing shared share sizing and buying-power/holdings validation logic.
 */
public abstract class AbstractEquityOrderExecutionStrategy implements OrderExecutionStrategy {

    protected static final int CURRENCY_SCALE = 2;

    /**
     * Resolves the unit execution price for the order (e.g., market close price or limit price).
     *
     * @param context The order execution context.
     * @return The resolved unit price in ARS.
     */
    protected abstract BigDecimal resolveExecutionPrice(OrderExecutionContext context);

    /**
     * Returns the target status when the order passes all buying-power/holdings validations
     * ({@link OrderStatus#FILLED} for MARKET, {@link OrderStatus#NEW} for LIMIT).
     *
     * @return The target {@link OrderStatus}.
     */
    protected abstract OrderStatus getAcceptedStatus();

    @Override
    public Order execute(OrderExecutionContext context) {
        PlaceOrderCommand command = context.command();
        if (context.instrument().isCurrency()) {
            throw new BusinessRuleViolationException(
                    "BUY and SELL equity orders cannot be placed on MONEDA instruments. Use CASH_IN or CASH_OUT."
            );
        }

        BigDecimal executionPrice = resolveExecutionPrice(context)
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);

        int resolvedSize = resolveShareSize(command, executionPrice);
        OrderStatus status = determineStatus(context, executionPrice, resolvedSize);

        return TradeOrder.builder()
                .instrumentId(command.instrumentId())
                .userId(command.userId())
                .size(resolvedSize)
                .price(executionPrice)
                .type(command.type())
                .side(command.side())
                .status(status)
                .datetime(LocalDateTime.now())
                .build();
    }

    private int resolveShareSize(PlaceOrderCommand command, BigDecimal executionPrice) {
        if (command.size() != null && command.size() > 0) {
            return command.size();
        }
        if (command.amount() != null && command.amount().compareTo(BigDecimal.ZERO) > 0) {
            return Order.calculateMaxShareSize(command.amount(), executionPrice);
        }
        return 0;
    }

    private OrderStatus determineStatus(
            OrderExecutionContext context,
            BigDecimal executionPrice,
            int resolvedSize
    ) {
        if (resolvedSize <= 0) {
            return OrderStatus.REJECTED;
        }

        OrderSide side = context.command().side();
        if (side == OrderSide.BUY) {
            BigDecimal totalCost = executionPrice.multiply(BigDecimal.valueOf(resolvedSize))
                    .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
            if (context.availableCash().compareTo(totalCost) < 0) {
                return OrderStatus.REJECTED;
            }
            return getAcceptedStatus();
        } else if (side == OrderSide.SELL) {
            if (context.availableShares() < resolvedSize) {
                return OrderStatus.REJECTED;
            }
            return getAcceptedStatus();
        }

        throw new BusinessRuleViolationException("Unsupported order side for equity instrument: " + side);
    }
}
