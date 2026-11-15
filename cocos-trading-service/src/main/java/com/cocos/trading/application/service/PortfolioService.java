package com.cocos.trading.application.service;

import com.cocos.trading.application.port.in.GetPortfolioUseCase;
import com.cocos.trading.application.port.out.MarketDataRepositoryPort;
import com.cocos.trading.application.port.out.OrderRepositoryPort;
import com.cocos.trading.application.port.out.UserRepositoryPort;
import com.cocos.trading.domain.exception.ResourceNotFoundException;
import com.cocos.trading.domain.model.AssetPosition;
import com.cocos.trading.domain.model.InstrumentType;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.Portfolio;
import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import com.cocos.trading.repository.entity.MarketDataJpaEntity;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import com.cocos.trading.repository.entity.TradeOrderJpaEntity;
import com.cocos.trading.repository.entity.UserJpaEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service implementing {@link GetPortfolioUseCase}.
 * Calculates a user's total account value, available ARS cash, and equity
 * positions
 * with market valuation, weighted average total return percentage, and daily
 * return percentage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService implements GetPortfolioUseCase {

    private static final int CURRENCY_SCALE = MonetaryAmount.CURRENCY_SCALE;
    private static final int CALCULATION_SCALE = 6;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final UserRepositoryPort userRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final MarketDataRepositoryPort marketDataRepositoryPort;

    @Override
    @Transactional(readOnly = true)
    public Portfolio getPortfolio(Long userId) {
        log.info("[portfolio] Calculating portfolio for userId={}", userId);
        UserJpaEntity user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        List<OrderJpaEntity> filledOrders = orderRepositoryPort.findByUserIdAndStatus(userId, OrderStatus.FILLED);
        MonetaryAmount availableCash = AccountLedgerCalculator.calculateAvailableCash(filledOrders);

        Set<Long> equityInstrumentIds = filledOrders.stream()
                .map(OrderJpaEntity::getInstrument)
                .filter(Objects::nonNull)
                .filter(inst -> inst.getType() == InstrumentType.ACCIONES)
                .map(InstrumentJpaEntity::getId)
                .collect(Collectors.toSet());

        Map<Long, MarketDataJpaEntity> latestMarketDataById = marketDataRepositoryPort
                .findLatestByInstrumentIds(equityInstrumentIds);

        Map<Long, PositionAccumulator> accumulators = new LinkedHashMap<>();
        for (OrderJpaEntity order : filledOrders) {
            if (!(order instanceof TradeOrderJpaEntity tradeOrder)) {
                continue;
            }
            InstrumentJpaEntity instrument = tradeOrder.getInstrument();
            if (instrument == null || instrument.getType() == InstrumentType.MONEDA) {
                continue;
            }
            PositionAccumulator acc = accumulators.computeIfAbsent(
                    instrument.getId(),
                    id -> new PositionAccumulator(instrument));
            acc.apply(tradeOrder);
        }

        List<AssetPosition> positions = new ArrayList<>();
        MonetaryAmount totalPositionsValue = MonetaryAmount.ZERO_ARS;

        for (Map.Entry<Long, PositionAccumulator> entry : accumulators.entrySet()) {
            Long instrumentId = entry.getKey();
            PositionAccumulator acc = entry.getValue();
            if (acc.quantity <= 0) {
                continue;
            }

            InstrumentJpaEntity instrument = acc.instrument;
            MarketDataJpaEntity marketData = latestMarketDataById.get(instrumentId);

            MonetaryAmount lastPrice = (marketData != null && marketData.getClose() != null)
                    ? MonetaryAmount.ars(marketData.getClose())
                    : MonetaryAmount.ars(acc.averageCost);

            MonetaryAmount marketValue = lastPrice.multiply(acc.quantity);

            BigDecimal totalReturnPercentage = calculateTotalReturnPercentage(lastPrice.amount(), acc.averageCost);
            BigDecimal dailyReturnPercentage = AccountLedgerCalculator.calculateDailyReturnPercentage(marketData);

            positions.add(AssetPosition.builder()
                    .instrument(instrument)
                    .quantity(acc.quantity)
                    .lastPrice(lastPrice)
                    .marketValue(marketValue)
                    .totalReturnPercentage(totalReturnPercentage)
                    .dailyReturnPercentage(dailyReturnPercentage)
                    .build());

            totalPositionsValue = totalPositionsValue.add(marketValue);
        }

        positions.sort(Comparator.comparing(pos -> pos.instrument().getTicker()));

        MonetaryAmount totalAccountValue = availableCash.add(totalPositionsValue);

        return Portfolio.builder()
                .user(user)
                .totalAccountValue(totalAccountValue)
                .availableCash(availableCash)
                .positions(positions)
                .build();
    }

    private BigDecimal calculateTotalReturnPercentage(BigDecimal currentPrice, BigDecimal averageCost) {
        if (averageCost == null || averageCost.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        }
        return currentPrice.subtract(averageCost)
                .divide(averageCost, CALCULATION_SCALE, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Stateful accumulator computing net share quantity and weighted average
     * purchase price (WAC).
     */
    private static final class PositionAccumulator {
        private final InstrumentJpaEntity instrument;
        private int quantity = 0;
        private BigDecimal totalCostBasis = BigDecimal.ZERO;
        private BigDecimal averageCost = BigDecimal.ZERO;

        private PositionAccumulator(InstrumentJpaEntity instrument) {
            this.instrument = instrument;
        }

        void apply(TradeOrderJpaEntity order) {
            if (order.getQuantity() == null || order.getQuantity() <= 0 || order.getPrice() == null) {
                return;
            }
            if (order.getSide() == OrderSide.BUY) {
                BigDecimal orderCost = order.getPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
                totalCostBasis = totalCostBasis.add(orderCost);
                quantity += order.getQuantity();
                averageCost = totalCostBasis.divide(BigDecimal.valueOf(quantity), CALCULATION_SCALE,
                        RoundingMode.HALF_UP);
            } else if (order.getSide() == OrderSide.SELL) {
                quantity -= order.getQuantity();
                if (quantity <= 0) {
                    quantity = 0;
                    totalCostBasis = BigDecimal.ZERO;
                    averageCost = BigDecimal.ZERO;
                } else {
                    totalCostBasis = averageCost.multiply(BigDecimal.valueOf(quantity));
                }
            }
        }
    }
}
