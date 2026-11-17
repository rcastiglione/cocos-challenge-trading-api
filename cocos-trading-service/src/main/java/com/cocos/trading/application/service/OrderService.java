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
import com.cocos.trading.domain.exception.IdempotencyMismatchException;
import com.cocos.trading.domain.exception.InvalidOrderStateException;
import com.cocos.trading.domain.exception.ResourceNotFoundException;
import com.cocos.trading.domain.model.InstrumentType;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import com.cocos.trading.repository.entity.MarketDataJpaEntity;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import com.cocos.trading.repository.entity.TradeOrderJpaEntity;
import com.cocos.trading.repository.entity.TransferOrderJpaEntity;
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
 * Application service implementing {@link PlaceOrderUseCase} and
 * {@link CancelOrderUseCase}.
 * Orchestrates order routing via polymorphic {@link OrderExecutionStrategy}
 * implementations,
 * manages order state transitions (such as cancellation), enforces distributed
 * idempotency locking
 * via {@link IdempotencyRepositoryPort}, and delegates telemetry recording to
 * {@link TradingMetricsService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService implements PlaceOrderUseCase, CancelOrderUseCase {

    public static final Duration IN_FLIGHT_LOCK_TTL = Duration.ofSeconds(30);
    public static final Duration IDEMPOTENCY_TTL = IN_FLIGHT_LOCK_TTL;

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
        String requestHash = idempotencyKey != null ? RequestHashUtils.sha256(command) : null;

        // Step A (Fast-Fail Concurrency): Acquire short-lived in-flight lock in Redis
        if (idempotencyKey != null) {
            boolean acquired = idempotencyRepositoryPort.tryAcquire(idempotencyKey, IN_FLIGHT_LOCK_TTL);
            if (!acquired) {
                log.warn("[order] Rejecting concurrent in-flight order submission for idempotencyKey={}",
                        idempotencyKey);
                throw new DuplicateOrderException(idempotencyKey);
            }
        }

        try {
            // Step B (Safe Replay + Stripe-style Payload Mismatch Guard):
            // Check if order was already persisted in PostgreSQL
            if (idempotencyKey != null) {
                Optional<OrderJpaEntity> existingOrder = orderRepositoryPort.findByIdempotencyKey(idempotencyKey);
                if (existingOrder.isPresent()) {
                    OrderJpaEntity persistedOrder = existingOrder.get();
                    if (persistedOrder.getRequestHash() != null
                            && !persistedOrder.getRequestHash().equals(requestHash)) {
                        log.warn("[order] Idempotency payload mismatch for idempotencyKey={}",
                                idempotencyKey);
                        throw new IdempotencyMismatchException(idempotencyKey);
                    }
                    log.info("[order] Idempotent replay returning existing orderId={} for idempotencyKey={}",
                            persistedOrder.getId(), idempotencyKey);
                    return persistedOrder;
                }
            }

            // Step C (Execution): Process order business logic and persist with idempotency_key + request_hash
            return executePlaceOrder(command, requestHash);
        } finally {
            if (idempotencyKey != null) {
                idempotencyRepositoryPort.release(idempotencyKey);
            }
        }
    }

    private OrderJpaEntity executePlaceOrder(PlaceOrderCommand command, String requestHash) {
        Timer.Sample executionSample = tradingMetricsService.startOrderTimer();
        log.info("[order] Placing order from command={}", command);

        UserJpaEntity user = userRepositoryPort.findById(command.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", command.userId()));

        InstrumentJpaEntity instrument = resolveInstrument(command);

        Optional<MarketDataJpaEntity> latestMarketData = instrument.getType() == InstrumentType.MONEDA
                ? Optional.empty()
                : marketDataRepositoryPort.findLatestByInstrumentId(instrument.getId());

        List<TradeOrderJpaEntity> filledTradeOrders =
                orderRepositoryPort.findTradeOrdersByUserIdAndStatus(command.userId(),
                        OrderStatus.FILLED);
        List<TransferOrderJpaEntity> filledTransferOrders =
                orderRepositoryPort.findTransferOrdersByUserIdAndStatus(command.userId(),
                        OrderStatus.FILLED);
        MonetaryAmount availableCash = AccountLedgerCalculator.calculateAvailableCash(
                filledTradeOrders, filledTransferOrders);
        long availableShares = AccountLedgerCalculator.calculateAvailableShares(filledTradeOrders,
                instrument.getId());

        OrderExecutionContext context = new OrderExecutionContext(
                command,
                user,
                instrument,
                latestMarketData,
                availableCash,
                availableShares);

        OrderExecutionStrategy strategy = executionStrategies.stream()
                .filter(s -> s.supports(context))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleViolationException(
                        String.format("No execution strategy found for order type=%s, side=%s",
                                command.type(), command.side())));

        OrderJpaEntity evaluatedOrder = strategy.execute(context);
        if (requestHash != null) {
            evaluatedOrder.setRequestHash(requestHash);
        }
        OrderJpaEntity savedOrder = orderRepositoryPort.save(evaluatedOrder);

        String status = savedOrder.getStatus().name();
        String type = savedOrder.getType().name();
        String side = savedOrder.getSide().name();
        tradingMetricsService.recordOrderProcessed(status, type, side);
        tradingMetricsService.stopOrderTimer(executionSample, status, type, side);

        log.info("[order] Order processed and persisted: id={}, status={}, quantity={}, price={}, idempotencyKey={}",
                savedOrder.getId(), savedOrder.getStatus(), savedOrder.getQuantity(),
                savedOrder.getPrice(), command.idempotencyKey());
        return savedOrder;
    }

    private InstrumentJpaEntity resolveInstrument(PlaceOrderCommand command) {
        if (command.side() != null && command.side().isCashOperation()) {
            String currencyTicker = (command.amount() != null && command.amount().currency() != null)
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

        return switch (existingOrder.getStatus()) {
            case CANCELLED -> {
                log.info("[order] Idempotent cancellation replay for already CANCELLED orderId={}", orderId);
                yield existingOrder;
            }
            case NEW -> {
                existingOrder.setStatus(OrderStatus.CANCELLED);
                OrderJpaEntity savedOrder = orderRepositoryPort.save(existingOrder);

                tradingMetricsService.recordOrderProcessed(
                        savedOrder.getStatus().name(),
                        savedOrder.getType().name(),
                        savedOrder.getSide().name());

                log.info("[order] Cancelled orderId={}", savedOrder.getId());
                yield savedOrder;
            }
            case FILLED, REJECTED -> throw new InvalidOrderStateException(
                    orderId,
                    existingOrder.getStatus(),
                    String.format("Cannot cancel order with terminal status %s. Only orders with status NEW can be cancelled.",
                            existingOrder.getStatus()));
        };
    }
}
