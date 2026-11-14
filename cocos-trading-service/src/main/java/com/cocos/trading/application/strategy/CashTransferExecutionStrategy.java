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
 * <p>
 * Cash transfers require a positive {@link MonetaryAmount} in {@code command.amount()} and reject {@code quantity}.
 * Internally, the monetary amount is mapped to the underlying database columns ({@code size} and {@code price = 1.00})
 * of {@link TransferOrderJpaEntity} to preserve 100% compatibility with the fixed PostgreSQL schema.
 * </p>
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
        if (command.quantity() != null) {
            throw new BusinessRuleViolationException(
                    "CASH_IN and CASH_OUT transfers must specify 'amount' (MonetaryAmount), not 'quantity'."
            );
        }
        if (command.amount() == null || !command.amount().isPositive()) {
            throw new BusinessRuleViolationException(
                    "CASH_IN and CASH_OUT transfers require a positive 'amount' (MonetaryAmount)."
            );
        }

        MonetaryAmount transferAmount = command.amount();
        validateCashWithdrawal(context, transferAmount);

        int resolvedUnits = AccountLedgerCalculator.calculateMaxShareQuantity(
                transferAmount,
                AccountLedgerCalculator.UNIT_CASH_PRICE
        );
        OrderStatus status = resolvedUnits > 0 ? OrderStatus.FILLED : OrderStatus.REJECTED;

        return TransferOrderJpaEntity.builder()
                .user(context.user())
                .instrument(context.instrument())
                .quantity(resolvedUnits)
                .price(AccountLedgerCalculator.UNIT_CASH_PRICE.amount())
                .type(command.type() != null ? command.type() : OrderType.MARKET)
                .side(command.side())
                .status(status)
                .datetime(LocalDateTime.now())
                .build();
    }

    private void validateCashWithdrawal(OrderExecutionContext context, MonetaryAmount transferAmount) {
        PlaceOrderCommand command = context.command();
        if (command.side() == OrderSide.CASH_OUT) {
            if (context.availableCash().isLessThan(transferAmount)) {
                throw new BusinessRuleViolationException(
                        String.format(
                                "Insufficient available cash for CASH_OUT transfer: requested=%s %s, available=%s %s.",
                                transferAmount.amount(),
                                transferAmount.currency(),
                                context.availableCash().amount(),
                                context.availableCash().currency()
                        )
                );
            }
        }
    }
}
