package com.cocos.trading.application.service;

import com.cocos.trading.application.port.in.GetPortfolioUseCase;
import com.cocos.trading.application.port.out.InstrumentRepositoryPort;
import com.cocos.trading.application.port.out.MarketDataRepositoryPort;
import com.cocos.trading.application.port.out.OrderRepositoryPort;
import com.cocos.trading.application.port.out.UserRepositoryPort;
import com.cocos.trading.domain.exception.ResourceNotFoundException;
import com.cocos.trading.domain.model.AssetPosition;
import com.cocos.trading.domain.model.Instrument;
import com.cocos.trading.domain.model.InstrumentType;
import com.cocos.trading.domain.model.MarketData;
import com.cocos.trading.domain.model.Order;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.Portfolio;
import com.cocos.trading.domain.model.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service implementing {@link GetPortfolioUseCase}.
 * Calculates a user's total account value, available ARS cash, and equity positions
 * with market valuation, weighted average total return percentage, and daily return percentage.
 */
@Service
public class PortfolioService implements GetPortfolioUseCase {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);
    private static final int CURRENCY_SCALE = 2;
    private static final int CALCULATION_SCALE = 6;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final UserRepositoryPort userRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final InstrumentRepositoryPort instrumentRepositoryPort;
    private final MarketDataRepositoryPort marketDataRepositoryPort;

    public PortfolioService(
            UserRepositoryPort userRepositoryPort,
            OrderRepositoryPort orderRepositoryPort,
            InstrumentRepositoryPort instrumentRepositoryPort,
            MarketDataRepositoryPort marketDataRepositoryPort
    ) {
        this.userRepositoryPort = userRepositoryPort;
        this.orderRepositoryPort = orderRepositoryPort;
        this.instrumentRepositoryPort = instrumentRepositoryPort;
        this.marketDataRepositoryPort = marketDataRepositoryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Portfolio getPortfolio(Long userId) {
        log.info("[portfolio] Calculating portfolio for userId={}", userId);
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        List<Order> filledOrders = orderRepositoryPort.findByUserIdAndStatus(userId, OrderStatus.FILLED);
        BigDecimal availableCash = AccountLedgerCalculator.calculateAvailableCash(filledOrders);

        Set<Long> tradedInstrumentIds = filledOrders.stream()
                .map(Order::instrumentId)
                .collect(Collectors.toSet());

        Map<Long, Instrument> instrumentsById = instrumentRepositoryPort.findAllByIds(tradedInstrumentIds).stream()
                .collect(Collectors.toMap(Instrument::id, Function.identity()));

        Set<Long> equityInstrumentIds = instrumentsById.values().stream()
                .filter(inst -> inst.type() == InstrumentType.ACCIONES)
                .map(Instrument::id)
                .collect(Collectors.toSet());

        Map<Long, MarketData> latestMarketDataById =
                marketDataRepositoryPort.findLatestByInstrumentIds(equityInstrumentIds);

        Map<Long, PositionAccumulator> accumulators = new LinkedHashMap<>();
        for (Order order : filledOrders) {
            Instrument instrument = instrumentsById.get(order.instrumentId());
            if (instrument == null || instrument.isCurrency()) {
                continue;
            }
            PositionAccumulator acc = accumulators.computeIfAbsent(
                    instrument.id(),
                    id -> new PositionAccumulator()
            );
            acc.apply(order);
        }

        List<AssetPosition> positions = new ArrayList<>();
        BigDecimal totalPositionsValue = BigDecimal.ZERO;

        for (Map.Entry<Long, PositionAccumulator> entry : accumulators.entrySet()) {
            Long instrumentId = entry.getKey();
            PositionAccumulator acc = entry.getValue();
            if (acc.quantity <= 0) {
                continue;
            }

            Instrument instrument = instrumentsById.get(instrumentId);
            MarketData marketData = latestMarketDataById.get(instrumentId);

            BigDecimal lastPrice = (marketData != null && marketData.close() != null)
                    ? marketData.close().setScale(CURRENCY_SCALE, RoundingMode.HALF_UP)
                    : acc.averageCost.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);

            BigDecimal marketValue = lastPrice.multiply(BigDecimal.valueOf(acc.quantity))
                    .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);

            BigDecimal totalReturnPercentage = calculateTotalReturnPercentage(lastPrice, acc.averageCost);
            BigDecimal dailyReturnPercentage = marketData != null
                    ? marketData.calculateDailyReturnPercentage()
                    : BigDecimal.ZERO.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);

            positions.add(new AssetPosition(
                    instrument.id(),
                    instrument.ticker(),
                    instrument.name(),
                    acc.quantity,
                    lastPrice,
                    marketValue,
                    totalReturnPercentage,
                    dailyReturnPercentage
            ));

            totalPositionsValue = totalPositionsValue.add(marketValue);
        }

        positions.sort(Comparator.comparing(AssetPosition::ticker));

        BigDecimal totalAccountValue = availableCash.add(totalPositionsValue)
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);

        return new Portfolio(
                user.id(),
                user.accountNumber(),
                totalAccountValue,
                availableCash,
                positions
        );
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
     * Stateful accumulator computing net share quantity and weighted average purchase price (WAC).
     */
    private static final class PositionAccumulator {
        private int quantity = 0;
        private BigDecimal totalCostBasis = BigDecimal.ZERO;
        private BigDecimal averageCost = BigDecimal.ZERO;

        void apply(Order order) {
            if (order.size() == null || order.size() <= 0 || order.price() == null) {
                return;
            }
            if (order.side() == OrderSide.BUY) {
                BigDecimal orderCost = order.price().multiply(BigDecimal.valueOf(order.size()));
                totalCostBasis = totalCostBasis.add(orderCost);
                quantity += order.size();
                averageCost = totalCostBasis.divide(BigDecimal.valueOf(quantity), CALCULATION_SCALE, RoundingMode.HALF_UP);
            } else if (order.side() == OrderSide.SELL) {
                quantity -= order.size();
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

