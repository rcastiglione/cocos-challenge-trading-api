package com.cocos.trading.application.service;

import com.cocos.trading.application.port.in.CancelOrderUseCase;
import com.cocos.trading.application.port.in.PlaceOrderUseCase;
import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.application.port.out.InstrumentRepositoryPort;
import com.cocos.trading.application.port.out.MarketDataRepositoryPort;
import com.cocos.trading.application.port.out.OrderRepositoryPort;
import com.cocos.trading.application.port.out.UserRepositoryPort;
import com.cocos.trading.application.strategy.OrderExecutionContext;
import com.cocos.trading.application.strategy.OrderExecutionStrategy;
import com.cocos.trading.domain.exception.BusinessRuleViolationException;
import com.cocos.trading.domain.exception.InvalidOrderStateException;
import com.cocos.trading.domain.exception.ResourceNotFoundException;
import com.cocos.trading.domain.model.Instrument;
import com.cocos.trading.domain.model.MarketData;
import com.cocos.trading.domain.model.Order;
import com.cocos.trading.domain.model.OrderStatus;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service implementing {@link PlaceOrderUseCase} and {@link CancelOrderUseCase}.
 * Orchestrates order routing via polymorphic {@link OrderExecutionStrategy} implementations
 * and delegates telemetry recording to {@link TradingMetricsService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService implements PlaceOrderUseCase, CancelOrderUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final InstrumentRepositoryPort instrumentRepositoryPort;
    private final MarketDataRepositoryPort marketDataRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final List<OrderExecutionStrategy> executionStrategies;
    private final TradingMetricsService tradingMetricsService;

    @Override
    @Transactional
    public Order placeOrder(PlaceOrderCommand command) {
        Timer.Sample executionSample = tradingMetricsService.startOrderTimer();
        log.info("[order] Placing order from command={}", command);

        userRepositoryPort.findById(command.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", command.userId()));

        Instrument instrument = instrumentRepositoryPort.findById(command.instrumentId())
                .orElseThrow(() -> new ResourceNotFoundException("Instrument", command.instrumentId()));

        Optional<MarketData> latestMarketData = instrument.isCurrency()
                ? Optional.empty()
                : marketDataRepositoryPort.findLatestByInstrumentId(instrument.id());

        List<Order> filledOrders = orderRepositoryPort.findByUserIdAndStatus(command.userId(), OrderStatus.FILLED);
        BigDecimal availableCash = AccountLedgerCalculator.calculateAvailableCash(filledOrders);
        int availableShares = AccountLedgerCalculator.calculateAvailableShares(filledOrders, instrument.id());

        OrderExecutionContext context = new OrderExecutionContext(
                command,
                instrument,
                latestMarketData,
                availableCash,
                availableShares
        );

        OrderExecutionStrategy strategy = executionStrategies.stream()
                .filter(s -> s.supports(context))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleViolationException(
                        String.format("No execution strategy found for order type=%s, side=%s", command.type(), command.side())
                ));

        Order evaluatedOrder = strategy.execute(context);
        Order savedOrder = orderRepositoryPort.save(evaluatedOrder);

        String status = savedOrder.getStatus().name();
        String type = savedOrder.getType().name();
        String side = savedOrder.getSide().name();
        tradingMetricsService.recordOrderProcessed(status, type, side);
        tradingMetricsService.stopOrderTimer(executionSample, status, type, side);

        log.info("[order] Order processed and persisted: id={}, status={}, size={}, price={}",
                savedOrder.getId(), savedOrder.getStatus(), savedOrder.getSize(), savedOrder.getPrice());
        return savedOrder;
    }

    @Override
    @Transactional
    public Order cancelOrder(Long orderId) {
        log.info("[order] Cancelling orderId={}", orderId);

        Order existingOrder = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (existingOrder.getStatus() != OrderStatus.NEW) {
            throw new InvalidOrderStateException(
                    orderId,
                    existingOrder.getStatus(),
                    String.format("Only orders with status NEW can be cancelled. Current status: %s", existingOrder.getStatus())
            );
        }

        Order cancelledOrder = existingOrder.cancel();
        Order savedOrder = orderRepositoryPort.save(cancelledOrder);

        tradingMetricsService.recordOrderProcessed(
                savedOrder.getStatus().name(),
                savedOrder.getType().name(),
                savedOrder.getSide().name()
        );

        log.info("[order] Cancelled orderId={}", savedOrder.getId());
        return savedOrder;
    }
}
