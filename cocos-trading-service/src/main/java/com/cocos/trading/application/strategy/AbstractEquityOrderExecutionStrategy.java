package com.cocos.trading.application.strategy;

import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.application.service.AccountLedgerCalculator;
import com.cocos.trading.domain.exception.BusinessRuleViolationException;
import com.cocos.trading.domain.model.InstrumentType;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import com.cocos.trading.repository.entity.TradeOrderJpaEntity;
import java.time.LocalDateTime;

/**
 * Abstract base class for equity ({@code ACCIONES}) order execution strategies,
 * providing shared share quantity calculation and buying-power/holdings validation logic.
 */
public abstract class AbstractEquityOrderExecutionStrategy implements OrderExecutionStrategy {

    /**
     * Resolves the unit execution price for the order (e.g., market close price or limit price).
     *
     * @param context The order execution context.
     * @return The resolved unit price as a {@link MonetaryAmount}.
     */
    protected abstract MonetaryAmount resolveExecutionPrice(OrderExecutionContext context);

    /**
     * Returns the target status when the order passes all buying-power/holdings validations
     * ({@link OrderStatus#FILLED} for MARKET, {@link OrderStatus#NEW} for LIMIT).
     *
     * @return The target {@link OrderStatus}.
     */
    protected abstract OrderStatus getAcceptedStatus();

    @Override
    public OrderJpaEntity execute(OrderExecutionContext context) {
        PlaceOrderCommand command = context.command();
        if (command.side() == null || command.side().isCashOperation()) {
            throw new BusinessRuleViolationException(
                    "Equity order execution requires a market trade side (BUY or SELL), but received: " + command.side()
            );
        }
        if (context.instrument().getType() == InstrumentType.MONEDA) {
            throw new BusinessRuleViolationException(
                    "BUY and SELL equity orders cannot be placed on MONEDA instruments. Use CASH_IN or CASH_OUT."
            );
        }

        MonetaryAmount executionPrice = resolveExecutionPrice(context);
        int resolvedQuantity = resolveShareQuantity(command, executionPrice);
        OrderStatus status = determineStatus(context, executionPrice, resolvedQuantity);

        return TradeOrderJpaEntity.builder()
                .user(context.user())
                .instrument(context.instrument())
                .quantity(resolvedQuantity)
                .price(executionPrice.amount())
                .type(command.type())
                .side(command.side())
                .status(status)
                .datetime(LocalDateTime.now())
                .build();
    }

    private int resolveShareQuantity(PlaceOrderCommand command, MonetaryAmount executionPrice) {
        if (command.quantity() != null && command.quantity() > 0) {
            return command.quantity();
        }
        if (command.amount() != null && command.amount().isPositive()) {
            return AccountLedgerCalculator.calculateMaxShareQuantity(command.amount(), executionPrice);
        }
        return 0;
    }

    private OrderStatus determineStatus(
            OrderExecutionContext context,
            MonetaryAmount executionPrice,
            int resolvedQuantity
    ) {
        if (resolvedQuantity <= 0) {
            return OrderStatus.REJECTED;
        }

        OrderSide side = context.command().side();
        if (side == OrderSide.BUY) {
            MonetaryAmount totalCost = executionPrice.multiply(resolvedQuantity);
            if (context.availableCash().isLessThan(totalCost)) {
                return OrderStatus.REJECTED;
            }
            return getAcceptedStatus();
        } else if (side == OrderSide.SELL) {
            if (context.availableShares() < resolvedQuantity) {
                return OrderStatus.REJECTED;
            }
            return getAcceptedStatus();
        }

        throw new BusinessRuleViolationException("Unsupported order side for equity instrument: " + side);
    }
}
