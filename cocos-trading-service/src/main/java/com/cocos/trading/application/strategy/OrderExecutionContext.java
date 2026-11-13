package com.cocos.trading.application.strategy;

import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.domain.model.Instrument;
import com.cocos.trading.domain.model.MarketData;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Context object containing the validated domain state required by an {@link OrderExecutionStrategy}
 * to evaluate and construct an {@link com.cocos.trading.domain.model.Order}.
 *
 * @param command          The incoming order placement command.
 * @param instrument       The target financial instrument.
 * @param latestMarketData The latest market data for the instrument (if applicable).
 * @param availableCash    The user's current available ARS cash balance.
 * @param availableShares  The user's current net share holdings for the instrument.
 */
public record OrderExecutionContext(
        PlaceOrderCommand command,
        Instrument instrument,
        Optional<MarketData> latestMarketData,
        BigDecimal availableCash,
        int availableShares
) {
}

