package com.cocos.trading.application.strategy;

import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import com.cocos.trading.repository.entity.MarketDataJpaEntity;
import com.cocos.trading.repository.entity.UserJpaEntity;
import java.util.Optional;

/**
 * Context object containing the validated state required by an {@link OrderExecutionStrategy}
 * to evaluate and construct an {@link com.cocos.trading.repository.entity.OrderJpaEntity}.
 *
 * @param command          The incoming order placement command.
 * @param user             The resolved {@link UserJpaEntity} placing the order.
 * @param instrument       The target financial {@link InstrumentJpaEntity}.
 * @param latestMarketData The latest market data for the instrument (if applicable).
 * @param availableCash    The user's current available ARS cash balance as a {@link MonetaryAmount}.
 * @param availableShares  The user's current net share holdings for the instrument.
 */
public record OrderExecutionContext(
        PlaceOrderCommand command,
        UserJpaEntity user,
        InstrumentJpaEntity instrument,
        Optional<MarketDataJpaEntity> latestMarketData,
        MonetaryAmount availableCash,
        long availableShares
) {
}
