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
import com.cocos.trading.domain.model.TradeOrder;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service implementing {@link GetPortfolioUseCase}.
 * Calculates a user's total account value, available ARS cash, and equity positions
 * with market valuation, weighted average total return percentage, and daily return percentage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService implements GetPortfolioUseCase {

    private static final int CURRENCY_SCALE = 2;
    private static final int CALCULATION_SCALE = 6;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final UserRepositoryPort userRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final InstrumentRepositoryPort instrumentRepositoryPort;
    private final MarketDataRepositoryPort marketDataRepositoryPort;

    @Override
    @Transactional(readOnly = true)
    public Portfolio getPortfolio(Long userId) {
        log.info("[portfolio] Calculating portfolio for userId={}", userId);
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        List<Order> filledOrders = orderRepositoryPort.findByUserIdAndStatus(userId, OrderStatus.FILLED);
        BigDecimal availableCash = AccountLedgerCalculator.calculateAvailableCash(filledOrders);

        Set<Long> tradedInstrumentIds = filledOrders.stream()
                .map(Order::getInstrumentId)
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
            if (!(order instanceof TradeOrder tradeOrder)) {
                continue;
            }
            Instrument instrument = instrumentsById.get(tradeOrder.getInstrumentId());
            if (instrument == null || instrument.isCurrency()) {
                continue;
            }
            PositionAccumulator acc = accumulators.computeIfAbsent(
                    instrument.id(),
                    id -> new PositionAccumulator()
            );
            acc.apply(tradeOrder);
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

            positions.add(AssetPosition.builder()
                    .instrumentId(instrument.id())
                    .ticker(instrument.ticker())
                    .name(instrument.name())
                    .quantity(acc.quantity)
                    .lastPrice(lastPrice)
                    .marketValue(marketValue)
                    .totalReturnPercentage(totalReturnPercentage)
                    .dailyReturnPercentage(dailyReturnPercentage)
                    .build());

            totalPositionsValue = totalPositionsValue.add(marketValue);
        }

        positions.sort(Comparator.comparing(AssetPosition::ticker));

        BigDecimal totalAccountValue = availableCash.add(totalPositionsValue)
                .setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);

        return Portfolio.builder()
                .userId(user.id())
                .accountNumber(user.accountNumber())
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
     * Stateful accumulator computing net share quantity and weighted average purchase price (WAC).
     */
    private static final class PositionAccumulator {
        private int quantity = 0;
        private BigDecimal totalCostBasis = BigDecimal.ZERO;
        private BigDecimal averageCost = BigDecimal.ZERO;

        void apply(TradeOrder order) {
            if (order.getSize() == null || order.getSize() <= 0 || order.getPrice() == null) {
                return;
            }
            if (order.getSide() == OrderSide.BUY) {
                BigDecimal orderCost = order.getPrice().multiply(BigDecimal.valueOf(order.getSize()));
                totalCostBasis = totalCostBasis.add(orderCost);
                quantity += order.getSize();
                averageCost = totalCostBasis.divide(BigDecimal.valueOf(quantity), CALCULATION_SCALE, RoundingMode.HALF_UP);
            } else if (order.getSide() == OrderSide.SELL) {
                quantity -= order.getSize();
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
