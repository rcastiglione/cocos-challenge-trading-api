package com.cocos.trading.application.service;

import com.cocos.trading.application.port.in.CancelOrderUseCase;
import com.cocos.trading.application.port.in.PlaceOrderUseCase;
import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.application.port.out.IdempotencyRepositoryPort;
import com.cocos.trading.application.port.out.InstrumentRepositoryPort;
import com.cocos.trading.application.port.out.MarketDataRepositoryPort;
import com.cocos.trading.application.port.out.OrderRepositoryPort;
import com.cocos.trading.application.port.out.UserRepositoryPort;
import com.cocos.trading.application.strategy.OrderExecutionContext;
import com.cocos.trading.application.strategy.OrderExecutionStrategy;
import com.cocos.trading.domain.exception.BusinessRuleViolationException;
import com.cocos.trading.domain.exception.DuplicateOrderException;
import com.cocos.trading.domain.exception.InvalidOrderStateException;
import com.cocos.trading.domain.exception.ResourceNotFoundException;
import com.cocos.trading.domain.model.InstrumentType;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import com.cocos.trading.repository.entity.MarketDataJpaEntity;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import com.cocos.trading.repository.entity.UserJpaEntity;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service implementing {@link PlaceOrderUseCase} and {@link CancelOrderUseCase}.
 * Orchestrates order routing via polymorphic {@link OrderExecutionStrategy} implementations,
 * manages order state transitions (such as cancellation), enforces distributed idempotency locking
 * via {@link IdempotencyRepositoryPort}, and delegates telemetry recording to {@link TradingMetricsService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService implements PlaceOrderUseCase, CancelOrderUseCase {

    public static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    // =================================================================================================================
    // TODO [Production Database Schema Constraint — UNIQUE(idempotency_key)]:
    // We currently enforce Idempotency and Distributed Locking exclusively via Redis (24-hour TTL) to strictly respect
    // the fixed PostgreSQL schema provided by the challenge evaluator (NeonDB / init.sql) without executing DDL
    // mutations. In a production environment, a permanent database-level constraint `UNIQUE(idempotency_key)` on the
    // `orders` table would be added via a future Flyway/Liquibase migration to complement the Redis fast-path lock.
    // =================================================================================================================
    private final IdempotencyRepositoryPort idempotencyRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;
    private final InstrumentRepositoryPort instrumentRepositoryPort;
    private final MarketDataRepositoryPort marketDataRepositoryPort;
    private final OrderRepositoryPort orderRepositoryPort;
    private final List<OrderExecutionStrategy> executionStrategies;
    private final TradingMetricsService tradingMetricsService;

    @Override
    @Transactional
    public OrderJpaEntity placeOrder(PlaceOrderCommand command) {
        UUID idempotencyKey = command.idempotencyKey();
        if (idempotencyKey != null) {
            boolean acquired = idempotencyRepositoryPort.tryAcquire(idempotencyKey, IDEMPOTENCY_TTL);
            if (!acquired) {
                log.warn("[order] Rejecting duplicate order submission for idempotencyKey={}", idempotencyKey);
                throw new DuplicateOrderException(idempotencyKey);
            }
        }

        try {
            return executePlaceOrder(command);
        } catch (RuntimeException ex) {
            if (idempotencyKey != null) {
                idempotencyRepositoryPort.release(idempotencyKey);
            }
            throw ex;
        }
    }

    private OrderJpaEntity executePlaceOrder(PlaceOrderCommand command) {
        Timer.Sample executionSample = tradingMetricsService.startOrderTimer();
        log.info("[order] Placing order from command={}", command);

        UserJpaEntity user = userRepositoryPort.findById(command.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", command.userId()));

        InstrumentJpaEntity instrument = resolveInstrument(command);

        Optional<MarketDataJpaEntity> latestMarketData = instrument.getType() == InstrumentType.MONEDA
                ? Optional.empty()
                : marketDataRepositoryPort.findLatestByInstrumentId(instrument.getId());

        List<OrderJpaEntity> filledOrders = orderRepositoryPort.findByUserIdAndStatus(command.userId(), OrderStatus.FILLED);
        MonetaryAmount availableCash = AccountLedgerCalculator.calculateAvailableCash(filledOrders);
        int availableShares = AccountLedgerCalculator.calculateAvailableShares(filledOrders, instrument.getId());

        OrderExecutionContext context = new OrderExecutionContext(
                command,
                user,
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

        OrderJpaEntity evaluatedOrder = strategy.execute(context);
        OrderJpaEntity savedOrder = orderRepositoryPort.save(evaluatedOrder);

        String status = savedOrder.getStatus().name();
        String type = savedOrder.getType().name();
        String side = savedOrder.getSide().name();
        tradingMetricsService.recordOrderProcessed(status, type, side);
        tradingMetricsService.stopOrderTimer(executionSample, status, type, side);

        log.info("[order] Order processed and persisted: id={}, status={}, quantity={}, price={}, idempotencyKey={}",
                savedOrder.getId(), savedOrder.getStatus(), savedOrder.getQuantity(), savedOrder.getPrice(), command.idempotencyKey());
        return savedOrder;
    }

    private InstrumentJpaEntity resolveInstrument(PlaceOrderCommand command) {
        if (command.side() != null && command.side().isCashOperation() && command.instrumentId() == null) {
            String currencyTicker = command.amount() != null
                    ? command.amount().currency()
                    : MonetaryAmount.ARS_CURRENCY;
            return instrumentRepositoryPort.findByTicker(currencyTicker)
                    .orElseThrow(() -> new ResourceNotFoundException("Instrument", currencyTicker));
        }
        return instrumentRepositoryPort.findById(command.instrumentId())
                .orElseThrow(() -> new ResourceNotFoundException("Instrument", command.instrumentId()));
    }

    @Override
    @Transactional
    public OrderJpaEntity cancelOrder(Long orderId) {
        log.info("[order] Cancelling orderId={}", orderId);

        OrderJpaEntity existingOrder = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (existingOrder.getStatus() != OrderStatus.NEW) {
            throw new InvalidOrderStateException(
                    orderId,
                    existingOrder.getStatus(),
                    String.format("Only orders with status NEW can be cancelled. Current status: %s", existingOrder.getStatus())
            );
        }

        existingOrder.setStatus(OrderStatus.CANCELLED);
        OrderJpaEntity savedOrder = orderRepositoryPort.save(existingOrder);

        tradingMetricsService.recordOrderProcessed(
                savedOrder.getStatus().name(),
                savedOrder.getType().name(),
                savedOrder.getSide().name()
        );

        log.info("[order] Cancelled orderId={}", savedOrder.getId());
        return savedOrder;
    }
}
