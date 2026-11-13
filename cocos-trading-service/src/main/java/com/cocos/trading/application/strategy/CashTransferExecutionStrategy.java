package com.cocos.trading.application.strategy;

import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.domain.exception.BusinessRuleViolationException;
import com.cocos.trading.domain.model.Order;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * Strategy for executing incoming ({@link OrderSide#CASH_IN}) and outgoing ({@link OrderSide#CASH_OUT})
 * cash transfer orders on {@code MONEDA} instruments.
 */
@Component
public class CashTransferExecutionStrategy implements OrderExecutionStrategy {

    private static final BigDecimal UNIT_CASH_PRICE = BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);

    @Override
    public boolean supports(OrderExecutionContext context) {
        return context.command().side().isCashOperation();
    }

    @Override
    public Order execute(OrderExecutionContext context) {
        PlaceOrderCommand command = context.command();
        if (!context.instrument().isCurrency()) {
            throw new BusinessRuleViolationException(
                    "CASH_IN and CASH_OUT orders can only be placed on MONEDA instruments."
            );
        }

        int resolvedSize = resolveCashUnits(command);
        OrderStatus status = determineCashStatus(context, resolvedSize);

        return new Order(
                null,
                command.instrumentId(),
                command.userId(),
                resolvedSize,
                UNIT_CASH_PRICE,
                command.type(),
                command.side(),
                status,
                LocalDateTime.now()
        );
    }

    private int resolveCashUnits(PlaceOrderCommand command) {
        if (command.size() != null && command.size() > 0) {
            return command.size();
        }
        if (command.amount() != null && command.amount().compareTo(BigDecimal.ZERO) > 0) {
            return Order.calculateMaxShareSize(command.amount(), UNIT_CASH_PRICE);
        }
        return 0;
    }

    private OrderStatus determineCashStatus(OrderExecutionContext context, int resolvedSize) {
        if (resolvedSize <= 0) {
            return OrderStatus.REJECTED;
        }
        if (context.command().side() == OrderSide.CASH_OUT) {
            BigDecimal withdrawalAmount = UNIT_CASH_PRICE.multiply(BigDecimal.valueOf(resolvedSize));
            if (context.availableCash().compareTo(withdrawalAmount) < 0) {
                return OrderStatus.REJECTED;
            }
        }
        return OrderStatus.FILLED;
    }
}

