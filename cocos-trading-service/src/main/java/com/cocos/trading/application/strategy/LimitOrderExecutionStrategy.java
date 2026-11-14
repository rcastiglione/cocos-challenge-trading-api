package com.cocos.trading.application.strategy;

import com.cocos.trading.domain.exception.BusinessRuleViolationException;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.OrderType;
import org.springframework.stereotype.Component;

/**
 * Strategy for executing {@link OrderType#LIMIT} equity orders (`BUY` or `SELL`).
 * Limit orders require an explicit user-supplied price and are persisted with status {@link OrderStatus#NEW}
 * when sufficient cash or shares are available.
 */
@Component
public class LimitOrderExecutionStrategy extends AbstractEquityOrderExecutionStrategy {

    @Override
    public boolean supports(OrderExecutionContext context) {
        return !context.command().side().isCashOperation()
                && context.command().type() == OrderType.LIMIT;
    }

    @Override
    protected MonetaryAmount resolveExecutionPrice(OrderExecutionContext context) {
        MonetaryAmount price = context.command().price();
        if (price == null || price.isZeroOrNegative()) {
            throw new BusinessRuleViolationException("Price is required and must be greater than 0 for LIMIT orders.");
        }
        return price;
    }

    @Override
    protected OrderStatus getAcceptedStatus() {
        return OrderStatus.NEW;
    }
}
