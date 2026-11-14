package com.cocos.trading.application.strategy;

import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.domain.exception.BusinessRuleViolationException;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.Order;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.TransferOrder;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * Strategy for executing incoming ({@link OrderSide#CASH_IN}) and outgoing ({@link OrderSide#CASH_OUT})
 * cash transfer orders on {@code MONEDA} instruments.
 */
@Component
public class CashTransferExecutionStrategy implements OrderExecutionStrategy {

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
        validateCashWithdrawal(context, resolvedSize);
        OrderStatus status = resolvedSize > 0 ? OrderStatus.FILLED : OrderStatus.REJECTED;

        return TransferOrder.builder()
                .instrumentId(command.instrumentId())
                .userId(command.userId())
                .size(resolvedSize)
                .price(TransferOrder.UNIT_CASH_PRICE)
                .type(command.type())
                .side(command.side())
                .status(status)
                .datetime(LocalDateTime.now())
                .build();
    }

    private int resolveCashUnits(PlaceOrderCommand command) {
        if (command.size() != null && command.size() > 0) {
            return command.size();
        }
        if (command.amount() != null && command.amount().isPositive()) {
            return Order.calculateMaxShareSize(command.amount(), TransferOrder.UNIT_CASH_PRICE);
        }
        return 0;
    }

    private void validateCashWithdrawal(OrderExecutionContext context, int resolvedSize) {
        PlaceOrderCommand command = context.command();
        if (command.side() == OrderSide.CASH_OUT) {
            MonetaryAmount withdrawalAmount = command.amount() != null && command.amount().isPositive()
                    ? command.amount()
                    : TransferOrder.UNIT_CASH_PRICE.multiply(resolvedSize);
            if (context.availableCash().isLessThan(withdrawalAmount)) {
                throw new BusinessRuleViolationException(
                        String.format(
                                "Insufficient available cash for CASH_OUT transfer: requested=%s %s, available=%s %s.",
                                withdrawalAmount.amount(),
                                withdrawalAmount.currency(),
                                context.availableCash().amount(),
                                context.availableCash().currency()
                        )
                );
            }
        }
    }
}
