package com.cocos.trading.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.application.port.out.IdempotencyRepositoryPort;
import com.cocos.trading.application.port.out.InstrumentRepositoryPort;
import com.cocos.trading.application.port.out.MarketDataRepositoryPort;
import com.cocos.trading.application.port.out.OrderRepositoryPort;
import com.cocos.trading.application.port.out.UserRepositoryPort;
import com.cocos.trading.application.strategy.CashTransferExecutionStrategy;
import com.cocos.trading.application.strategy.LimitOrderExecutionStrategy;
import com.cocos.trading.application.strategy.MarketOrderExecutionStrategy;
import com.cocos.trading.domain.exception.BusinessRuleViolationException;
import com.cocos.trading.domain.exception.DuplicateOrderException;
import com.cocos.trading.domain.exception.InvalidOrderStateException;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.OrderType;
import com.cocos.trading.fixture.TradingFixtures;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import com.cocos.trading.repository.entity.TradeOrderJpaEntity;
import com.cocos.trading.repository.entity.TransferOrderJpaEntity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Order Service & Execution Strategies Unit Tests")
class OrderServiceTest {

    @Mock
    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private InstrumentRepositoryPort instrumentRepositoryPort;

    @Mock
    private MarketDataRepositoryPort marketDataRepositoryPort;

    @Mock
    private OrderRepositoryPort orderRepositoryPort;

    private SimpleMeterRegistry meterRegistry;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        TradingMetricsService tradingMetricsService = new TradingMetricsService(meterRegistry);
        lenient().when(idempotencyRepositoryPort.tryAcquire(any(UUID.class), eq(OrderService.IDEMPOTENCY_TTL)))
                .thenReturn(true);
        orderService = new OrderService(
                idempotencyRepositoryPort,
                userRepositoryPort,
                instrumentRepositoryPort,
                marketDataRepositoryPort,
                orderRepositoryPort,
                List.of(
                        new MarketOrderExecutionStrategy(),
                        new LimitOrderExecutionStrategy(),
                        new CashTransferExecutionStrategy()
                ),
                tradingMetricsService
        );
    }

    @Test
    @DisplayName("Should execute MARKET BUY order by exact quantity and persist as FILLED when cash is sufficient")
    void shouldExecuteMarketBuyByQuantityAsFilled() {
        PlaceOrderCommand command = TradingFixtures.marketBuyByQuantity(10);

        when(userRepositoryPort.findById(TradingFixtures.DEFAULT_USER_ID))
                .thenReturn(Optional.of(TradingFixtures.defaultUser()));
        when(instrumentRepositoryPort.findById(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampInstrument()));
        when(marketDataRepositoryPort.findLatestByInstrumentId(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampMarketData()));
        when(orderRepositoryPort.findByUserIdAndStatus(TradingFixtures.DEFAULT_USER_ID, OrderStatus.FILLED))
                .thenReturn(List.of(TradingFixtures.cashInOrder(1L, 50_000)));
        when(orderRepositoryPort.save(any(OrderJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderJpaEntity result = orderService.placeOrder(command);

        assertInstanceOf(TradeOrderJpaEntity.class, result);
        assertEquals(OrderStatus.FILLED, result.getStatus());
        assertEquals(10, result.getQuantity());
        assertEquals(new BigDecimal("925.85"), result.getPrice());
        assertNotNull(result.getUser());
        assertEquals(TradingFixtures.DEFAULT_USER_ID, result.getUser().getId());
        assertEquals("10001", result.getUser().getAccountNumber());
        assertNotNull(result.getInstrument());
        assertEquals(TradingFixtures.PAMP_INSTRUMENT_ID, result.getInstrument().getId());
        assertEquals("PAMP", result.getInstrument().getTicker());
        verify(orderRepositoryPort).save(any(OrderJpaEntity.class));

        Counter filledCounter = meterRegistry.find(TradingMetricsService.ORDERS_PROCESSED_METRIC)
                .tags("status", "FILLED", "type", "MARKET", "side", "BUY")
                .counter();
        assertNotNull(filledCounter);
        assertEquals(1.0, filledCounter.count());

        Timer executionTimer = meterRegistry.find(TradingMetricsService.ORDER_EXECUTION_LATENCY_METRIC)
                .tags("status", "FILLED", "type", "MARKET", "side", "BUY")
                .timer();
        assertNotNull(executionTimer);
        assertEquals(1L, executionTimer.count());
    }

    @Test
    @DisplayName("Should calculate whole share quantity from total ARS amount on MARKET BUY order")
    void shouldCalculateShareQuantityFromInvestmentAmount() {
        // 10,000 ARS / 925.85 = 10 shares
        PlaceOrderCommand command = TradingFixtures.marketBuyByAmount("10000.00");

        when(userRepositoryPort.findById(TradingFixtures.DEFAULT_USER_ID))
                .thenReturn(Optional.of(TradingFixtures.defaultUser()));
        when(instrumentRepositoryPort.findById(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampInstrument()));
        when(marketDataRepositoryPort.findLatestByInstrumentId(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampMarketData()));
        when(orderRepositoryPort.findByUserIdAndStatus(TradingFixtures.DEFAULT_USER_ID, OrderStatus.FILLED))
                .thenReturn(List.of(TradingFixtures.cashInOrder(1L, 50_000)));
        when(orderRepositoryPort.save(any(OrderJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderJpaEntity result = orderService.placeOrder(command);

        assertInstanceOf(TradeOrderJpaEntity.class, result);
        assertEquals(OrderStatus.FILLED, result.getStatus());
        assertEquals(10, result.getQuantity());
        assertEquals(new BigDecimal("925.85"), result.getPrice());
    }

    @Test
    @DisplayName("Should reject and persist as REJECTED when user has insufficient cash for BUY order")
    void shouldRejectOrderWhenInsufficientCash() {
        // 100 shares * 925.85 = 92,585.00 ARS > 10,000.00 ARS available
        PlaceOrderCommand command = TradingFixtures.marketBuyByQuantity(100);

        when(userRepositoryPort.findById(TradingFixtures.DEFAULT_USER_ID))
                .thenReturn(Optional.of(TradingFixtures.defaultUser()));
        when(instrumentRepositoryPort.findById(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampInstrument()));
        when(marketDataRepositoryPort.findLatestByInstrumentId(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampMarketData()));
        when(orderRepositoryPort.findByUserIdAndStatus(TradingFixtures.DEFAULT_USER_ID, OrderStatus.FILLED))
                .thenReturn(List.of(TradingFixtures.cashInOrder(1L, 10_000)));
        when(orderRepositoryPort.save(any(OrderJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderJpaEntity result = orderService.placeOrder(command);

        ArgumentCaptor<OrderJpaEntity> captor = ArgumentCaptor.forClass(OrderJpaEntity.class);
        verify(orderRepositoryPort).save(captor.capture());
        assertEquals(OrderStatus.REJECTED, result.getStatus());
        assertEquals(OrderStatus.REJECTED, captor.getValue().getStatus());

        Counter rejectedCounter = meterRegistry.find(TradingMetricsService.ORDERS_PROCESSED_METRIC)
                .tags("status", "REJECTED", "type", "MARKET", "side", "BUY")
                .counter();
        assertNotNull(rejectedCounter);
        assertEquals(1.0, rejectedCounter.count());
    }

    @Test
    @DisplayName("Should reject and persist as REJECTED when user has insufficient shares for SELL order")
    void shouldRejectOrderWhenInsufficientSharesForSell() {
        PlaceOrderCommand command = PlaceOrderCommand.builder()
                .userId(TradingFixtures.DEFAULT_USER_ID)
                .instrumentId(TradingFixtures.PAMP_INSTRUMENT_ID)
                .side(OrderSide.SELL)
                .type(OrderType.MARKET)
                .quantity(50)
                .build();

        when(userRepositoryPort.findById(TradingFixtures.DEFAULT_USER_ID))
                .thenReturn(Optional.of(TradingFixtures.defaultUser()));
        when(instrumentRepositoryPort.findById(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampInstrument()));
        when(marketDataRepositoryPort.findLatestByInstrumentId(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampMarketData()));
        // User only holds 10 shares of PAMP
        when(orderRepositoryPort.findByUserIdAndStatus(TradingFixtures.DEFAULT_USER_ID, OrderStatus.FILLED))
                .thenReturn(List.of(
                        TradingFixtures.cashInOrder(1L, 50_000),
                        TradingFixtures.filledBuyOrder(2L, TradingFixtures.PAMP_INSTRUMENT_ID, 10, "800.00")
                ));
        when(orderRepositoryPort.save(any(OrderJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderJpaEntity result = orderService.placeOrder(command);

        assertEquals(OrderStatus.REJECTED, result.getStatus());
        assertEquals(50, result.getQuantity());
    }

    @Test
    @DisplayName("Should persist LIMIT order with status NEW when buying power is sufficient")
    void shouldPersistLimitOrderAsNew() {
        PlaceOrderCommand command = PlaceOrderCommand.builder()
                .userId(TradingFixtures.DEFAULT_USER_ID)
                .instrumentId(TradingFixtures.PAMP_INSTRUMENT_ID)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .quantity(10)
                .price(MonetaryAmount.ars("900.00"))
                .build();

        when(userRepositoryPort.findById(TradingFixtures.DEFAULT_USER_ID))
                .thenReturn(Optional.of(TradingFixtures.defaultUser()));
        when(instrumentRepositoryPort.findById(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampInstrument()));
        when(marketDataRepositoryPort.findLatestByInstrumentId(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampMarketData()));
        when(orderRepositoryPort.findByUserIdAndStatus(TradingFixtures.DEFAULT_USER_ID, OrderStatus.FILLED))
                .thenReturn(List.of(TradingFixtures.cashInOrder(1L, 20_000)));
        when(orderRepositoryPort.save(any(OrderJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderJpaEntity result = orderService.placeOrder(command);

        assertEquals(OrderStatus.NEW, result.getStatus());
        assertEquals(10, result.getQuantity());
        assertEquals(new BigDecimal("900.00"), result.getPrice());

        Counter newCounter = meterRegistry.find(TradingMetricsService.ORDERS_PROCESSED_METRIC)
                .tags("status", "NEW", "type", "LIMIT", "side", "BUY")
                .counter();
        assertNotNull(newCounter);
        assertEquals(1.0, newCounter.count());
    }

    @Test
    @DisplayName("Should cancel order when status is NEW and reject cancellation when status is FILLED")
    void shouldOnlyCancelOrdersWithNewStatus() {
        OrderJpaEntity newOrder = TradeOrderJpaEntity.builder()
                .id(50L)
                .user(TradingFixtures.defaultUser())
                .instrument(TradingFixtures.pampInstrument())
                .quantity(10)
                .price(new BigDecimal("900.00"))
                .type(OrderType.LIMIT)
                .side(OrderSide.BUY)
                .status(OrderStatus.NEW)
                .datetime(LocalDateTime.now())
                .build();
        when(orderRepositoryPort.findById(50L)).thenReturn(Optional.of(newOrder));
        when(orderRepositoryPort.save(any(OrderJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderJpaEntity cancelled = orderService.cancelOrder(50L);
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertNotNull(cancelled.getUser());
        assertEquals("10001", cancelled.getUser().getAccountNumber());
        assertNotNull(cancelled.getInstrument());
        assertEquals("PAMP", cancelled.getInstrument().getTicker());

        Counter cancelledCounter = meterRegistry.find(TradingMetricsService.ORDERS_PROCESSED_METRIC)
                .tags("status", "CANCELLED", "type", "LIMIT", "side", "BUY")
                .counter();
        assertNotNull(cancelledCounter);
        assertEquals(1.0, cancelledCounter.count());

        OrderJpaEntity filledOrder = TradingFixtures.filledBuyOrder(51L, TradingFixtures.PAMP_INSTRUMENT_ID, 10, "900.00");
        when(orderRepositoryPort.findById(51L)).thenReturn(Optional.of(filledOrder));

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder(51L));
    }

    @Test
    @DisplayName("Should isolate Timer.Sample per thread and record exact Counter and Timer totals under concurrent load")
    void shouldMaintainThreadSafetyUnderHighConcurrency() throws Exception {
        int concurrentRequests = 50;
        PlaceOrderCommand command = TradingFixtures.marketBuyByQuantity(10);

        when(userRepositoryPort.findById(TradingFixtures.DEFAULT_USER_ID))
                .thenReturn(Optional.of(TradingFixtures.defaultUser()));
        when(instrumentRepositoryPort.findById(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampInstrument()));
        when(marketDataRepositoryPort.findLatestByInstrumentId(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampMarketData()));
        when(orderRepositoryPort.findByUserIdAndStatus(TradingFixtures.DEFAULT_USER_ID, OrderStatus.FILLED))
                .thenReturn(List.of(TradingFixtures.cashInOrder(1L, 50_000)));
        when(orderRepositoryPort.save(any(OrderJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CountDownLatch readyLatch = new CountDownLatch(concurrentRequests);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            List<Future<OrderJpaEntity>> futures = new ArrayList<>(concurrentRequests);
            for (int i = 0; i < concurrentRequests; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startGate.await(5, TimeUnit.SECONDS);
                    return orderService.placeOrder(command);
                }));
            }

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
            startGate.countDown();

            for (Future<OrderJpaEntity> future : futures) {
                OrderJpaEntity order = future.get(5, TimeUnit.SECONDS);
                assertEquals(OrderStatus.FILLED, order.getStatus());
            }
        } finally {
            executor.shutdownNow();
        }

        Counter filledCounter = meterRegistry.find(TradingMetricsService.ORDERS_PROCESSED_METRIC)
                .tags("status", "FILLED", "type", "MARKET", "side", "BUY")
                .counter();
        assertNotNull(filledCounter);
        assertEquals((double) concurrentRequests, filledCounter.count());

        Timer executionTimer = meterRegistry.find(TradingMetricsService.ORDER_EXECUTION_LATENCY_METRIC)
                .tags("status", "FILLED", "type", "MARKET", "side", "BUY")
                .timer();
        assertNotNull(executionTimer);
        assertEquals(concurrentRequests, executionTimer.count());
        assertTrue(executionTimer.totalTime(TimeUnit.NANOSECONDS) > 0);
    }

    @Test
    @DisplayName("Should execute CASH_OUT transfer as FILLED resolving ARS instrument by ticker when requested amount does not exceed availableCash")
    void shouldExecuteCashOutTransferSuccessfullyWhenCashIsSufficient() {
        PlaceOrderCommand command = PlaceOrderCommand.builder()
                .userId(TradingFixtures.DEFAULT_USER_ID)
                .side(OrderSide.CASH_OUT)
                .type(OrderType.MARKET)
                .amount(MonetaryAmount.ars("20000.00"))
                .build();

        when(userRepositoryPort.findById(TradingFixtures.DEFAULT_USER_ID))
                .thenReturn(Optional.of(TradingFixtures.defaultUser()));
        when(instrumentRepositoryPort.findByTicker("ARS"))
                .thenReturn(Optional.of(TradingFixtures.arsInstrument()));
        when(orderRepositoryPort.findByUserIdAndStatus(TradingFixtures.DEFAULT_USER_ID, OrderStatus.FILLED))
                .thenReturn(List.of(TradingFixtures.cashInOrder(1L, 50_000)));
        when(orderRepositoryPort.save(any(OrderJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderJpaEntity result = orderService.placeOrder(command);

        assertInstanceOf(TransferOrderJpaEntity.class, result);
        assertEquals(OrderStatus.FILLED, result.getStatus());
        assertEquals(OrderSide.CASH_OUT, result.getSide());
        assertEquals(20_000, result.getQuantity());
        assertEquals(new BigDecimal("1.00"), result.getPrice());
        assertEquals(MonetaryAmount.ars("-20000.00"), AccountLedgerCalculator.calculateCashImpact(result));

        Counter cashOutCounter = meterRegistry.find(TradingMetricsService.ORDERS_PROCESSED_METRIC)
                .tags("status", "FILLED", "type", "MARKET", "side", "CASH_OUT")
                .counter();
        assertNotNull(cashOutCounter);
        assertEquals(1.0, cashOutCounter.count());
    }

    @Test
    @DisplayName("Should throw BusinessRuleViolationException when CASH_OUT requested amount exceeds availableCash")
    void shouldThrowBusinessRuleViolationWhenCashOutExceedsAvailableCash() {
        PlaceOrderCommand command = PlaceOrderCommand.builder()
                .idempotencyKey(UUID.randomUUID())
                .userId(TradingFixtures.DEFAULT_USER_ID)
                .side(OrderSide.CASH_OUT)
                .type(OrderType.MARKET)
                .amount(MonetaryAmount.ars("60000.00"))
                .build();

        when(userRepositoryPort.findById(TradingFixtures.DEFAULT_USER_ID))
                .thenReturn(Optional.of(TradingFixtures.defaultUser()));
        when(instrumentRepositoryPort.findByTicker("ARS"))
                .thenReturn(Optional.of(TradingFixtures.arsInstrument()));
        when(orderRepositoryPort.findByUserIdAndStatus(TradingFixtures.DEFAULT_USER_ID, OrderStatus.FILLED))
                .thenReturn(List.of(TradingFixtures.cashInOrder(1L, 50_000)));

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> orderService.placeOrder(command)
        );
        assertTrue(exception.getMessage().contains("Insufficient available cash for CASH_OUT transfer"));
        verify(idempotencyRepositoryPort).release(command.idempotencyKey());
    }

    @Test
    @DisplayName("Should reject duplicate order with DuplicateOrderException when Idempotency-Key already exists in Redis")
    void shouldRejectDuplicateOrderWhenIdempotencyKeyAlreadyExists() {
        UUID duplicateKey = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        PlaceOrderCommand command = PlaceOrderCommand.builder()
                .idempotencyKey(duplicateKey)
                .userId(TradingFixtures.DEFAULT_USER_ID)
                .instrumentId(TradingFixtures.PAMP_INSTRUMENT_ID)
                .side(OrderSide.BUY)
                .type(OrderType.MARKET)
                .quantity(10)
                .build();

        when(idempotencyRepositoryPort.tryAcquire(duplicateKey, Duration.ofHours(24)))
                .thenReturn(false);

        DuplicateOrderException exception = assertThrows(
                DuplicateOrderException.class,
                () -> orderService.placeOrder(command)
        );

        assertEquals(duplicateKey, exception.getIdempotencyKey());
        verify(orderRepositoryPort, never()).save(any());
        verify(idempotencyRepositoryPort, never()).release(any());
    }

    @Test
    @DisplayName("Should prevent double-spending when multiple concurrent threads submit the same Idempotency-Key")
    void shouldPreventDoubleSpendingWhenSameIdempotencyKeySubmittedConcurrently() throws Exception {
        int concurrentAttempts = 20;
        UUID sharedIdempotencyKey = UUID.randomUUID();
        PlaceOrderCommand command = PlaceOrderCommand.builder()
                .idempotencyKey(sharedIdempotencyKey)
                .userId(TradingFixtures.DEFAULT_USER_ID)
                .side(OrderSide.CASH_OUT)
                .type(OrderType.MARKET)
                .amount(MonetaryAmount.ars("25000.00"))
                .build();

        Set<UUID> lockedKeys = ConcurrentHashMap.newKeySet();
        when(idempotencyRepositoryPort.tryAcquire(eq(sharedIdempotencyKey), eq(OrderService.IDEMPOTENCY_TTL)))
                .thenAnswer(inv -> lockedKeys.add(inv.getArgument(0)));

        when(userRepositoryPort.findById(TradingFixtures.DEFAULT_USER_ID))
                .thenReturn(Optional.of(TradingFixtures.defaultUser()));
        when(instrumentRepositoryPort.findByTicker("ARS"))
                .thenReturn(Optional.of(TradingFixtures.arsInstrument()));
        when(orderRepositoryPort.findByUserIdAndStatus(TradingFixtures.DEFAULT_USER_ID, OrderStatus.FILLED))
                .thenReturn(List.of(TradingFixtures.cashInOrder(1L, 50_000)));
        when(orderRepositoryPort.save(any(OrderJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CountDownLatch readyLatch = new CountDownLatch(concurrentAttempts);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        try {
            List<Future<OrderJpaEntity>> futures = new ArrayList<>(concurrentAttempts);
            for (int i = 0; i < concurrentAttempts; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startGate.await(5, TimeUnit.SECONDS);
                    return orderService.placeOrder(command);
                }));
            }

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
            startGate.countDown();

            for (Future<OrderJpaEntity> future : futures) {
                try {
                    OrderJpaEntity order = future.get(5, TimeUnit.SECONDS);
                    if (order.getStatus() == OrderStatus.FILLED) {
                        successCount.incrementAndGet();
                    }
                } catch (ExecutionException ex) {
                    if (ex.getCause() instanceof DuplicateOrderException) {
                        duplicateCount.incrementAndGet();
                    } else {
                        throw ex;
                    }
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, successCount.get());
        assertEquals(concurrentAttempts - 1, duplicateCount.get());
        verify(orderRepositoryPort, times(1)).save(any(OrderJpaEntity.class));
    }
}
