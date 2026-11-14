package com.cocos.trading.application.strategy;

import com.cocos.trading.domain.exception.ResourceNotFoundException;
import com.cocos.trading.domain.model.MarketData;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.OrderType;
import org.springframework.stereotype.Component;

/**
 * Strategy for executing {@link OrderType#MARKET} equity orders (`BUY` or `SELL`).
 * Market orders use the latest {@link MarketData#close()} price and transition immediately to {@link OrderStatus#FILLED}
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
        MarketData marketData = context.latestMarketData()
                .orElseThrow(() -> new ResourceNotFoundException("MarketData for instrument", context.instrument().id()));
        if (marketData.close() == null || marketData.close().isZeroOrNegative()) {
            throw new ResourceNotFoundException("Valid close price for instrument", context.instrument().id());
        }
        return marketData.close();
    }

    @Override
    protected OrderStatus getAcceptedStatus() {
        return OrderStatus.FILLED;
    }
}
