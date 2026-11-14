package com.cocos.trading.application.strategy;

import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.application.service.AccountLedgerCalculator;
import com.cocos.trading.domain.exception.BusinessRuleViolationException;
import com.cocos.trading.domain.model.InstrumentType;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.OrderType;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import com.cocos.trading.repository.entity.TransferOrderJpaEntity;
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
    public OrderJpaEntity execute(OrderExecutionContext context) {
        PlaceOrderCommand command = context.command();
        if (command.side() == null || !command.side().isCashOperation()) {
            throw new BusinessRuleViolationException(
                    "CashTransferExecutionStrategy requires a cash movement side (CASH_IN or CASH_OUT), but received: " + command.side()
            );
        }
        if (context.instrument().getType() != InstrumentType.MONEDA) {
            throw new BusinessRuleViolationException(
                    "CASH_IN and CASH_OUT orders can only be placed on MONEDA instruments."
            );
        }

        int resolvedQuantity = resolveCashUnits(command);
        validateCashWithdrawal(context, resolvedQuantity);
        OrderStatus status = resolvedQuantity > 0 ? OrderStatus.FILLED : OrderStatus.REJECTED;

        return TransferOrderJpaEntity.builder()
                .user(context.user())
                .instrument(context.instrument())
                .quantity(resolvedQuantity)
                .price(AccountLedgerCalculator.UNIT_CASH_PRICE.amount())
                .type(command.type() != null ? command.type() : OrderType.MARKET)
                .side(command.side())
                .status(status)
                .datetime(LocalDateTime.now())
                .build();
    }

    private int resolveCashUnits(PlaceOrderCommand command) {
        if (command.quantity() != null && command.quantity() > 0) {
            return command.quantity();
        }
        if (command.amount() != null && command.amount().isPositive()) {
            return AccountLedgerCalculator.calculateMaxShareQuantity(command.amount(), AccountLedgerCalculator.UNIT_CASH_PRICE);
        }
        return 0;
    }

    private void validateCashWithdrawal(OrderExecutionContext context, int resolvedQuantity) {
        PlaceOrderCommand command = context.command();
        if (command.side() == OrderSide.CASH_OUT) {
            MonetaryAmount withdrawalAmount = command.amount() != null && command.amount().isPositive()
                    ? command.amount()
                    : AccountLedgerCalculator.UNIT_CASH_PRICE.multiply(resolvedQuantity);
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
