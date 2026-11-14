package com.cocos.trading.application.strategy;

import com.cocos.trading.domain.exception.ResourceNotFoundException;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.OrderType;
import com.cocos.trading.repository.entity.MarketDataJpaEntity;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Strategy for executing {@link OrderType#MARKET} equity orders (`BUY` or `SELL`).
 * Market orders use the latest {@link MarketDataJpaEntity#getClose()} price and transition immediately to {@link OrderStatus#FILLED}
 * when sufficient cash or shares are available.
 */
@Component
public class MarketOrderExecutionStrategy extends AbstractEquityOrderExecutionStrategy {

    @Override
    public boolean supports(OrderExecutionContext context) {
        return !context.command().side().isCashOperation()
                && context.command().type() == OrderType.MARKET;
    }

    @Override
    protected MonetaryAmount resolveExecutionPrice(OrderExecutionContext context) {
        MarketDataJpaEntity marketData = context.latestMarketData()
                .orElseThrow(() -> new ResourceNotFoundException("MarketData for instrument", context.instrument().getId()));
        if (marketData.getClose() == null || marketData.getClose().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResourceNotFoundException("Valid close price for instrument", context.instrument().getId());
        }
        return MonetaryAmount.ars(marketData.getClose());
    }

    @Override
    protected OrderStatus getAcceptedStatus() {
        return OrderStatus.FILLED;
    }
}
