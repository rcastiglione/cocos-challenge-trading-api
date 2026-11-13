package com.cocos.trading.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.application.port.out.InstrumentRepositoryPort;
import com.cocos.trading.application.port.out.MarketDataRepositoryPort;
import com.cocos.trading.application.port.out.OrderRepositoryPort;
import com.cocos.trading.application.port.out.UserRepositoryPort;
import com.cocos.trading.application.strategy.CashTransferExecutionStrategy;
import com.cocos.trading.application.strategy.LimitOrderExecutionStrategy;
import com.cocos.trading.application.strategy.MarketOrderExecutionStrategy;
import com.cocos.trading.domain.exception.InvalidOrderStateException;
import com.cocos.trading.domain.model.Order;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.OrderType;
import com.cocos.trading.fixture.TradingFixtures;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private InstrumentRepositoryPort instrumentRepositoryPort;

    @Mock
    private MarketDataRepositoryPort marketDataRepositoryPort;

    @Mock
    private OrderRepositoryPort orderRepositoryPort;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                userRepositoryPort,
                instrumentRepositoryPort,
                marketDataRepositoryPort,
                orderRepositoryPort,
                List.of(
                        new MarketOrderExecutionStrategy(),
                        new LimitOrderExecutionStrategy(),
                        new CashTransferExecutionStrategy()
                )
        );
    }

    @Test
    @DisplayName("Should execute MARKET BUY order by exact size and persist as FILLED when cash is sufficient")
    void shouldExecuteMarketBuyBySizeAsFilled() {
        PlaceOrderCommand command = TradingFixtures.marketBuyBySize(10);

        when(userRepositoryPort.findById(TradingFixtures.DEFAULT_USER_ID))
                .thenReturn(Optional.of(TradingFixtures.defaultUser()));
        when(instrumentRepositoryPort.findById(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampInstrument()));
        when(marketDataRepositoryPort.findLatestByInstrumentId(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampMarketData()));
        when(orderRepositoryPort.findByUserIdAndStatus(TradingFixtures.DEFAULT_USER_ID, OrderStatus.FILLED))
                .thenReturn(List.of(TradingFixtures.cashInOrder(1L, 50_000)));
        when(orderRepositoryPort.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.placeOrder(command);

        assertEquals(OrderStatus.FILLED, result.status());
        assertEquals(10, result.size());
        assertEquals(new BigDecimal("925.85"), result.price());
        verify(orderRepositoryPort).save(any(Order.class));
    }

    @Test
    @DisplayName("Should calculate whole share size from total ARS amount on MARKET BUY order")
    void shouldCalculateShareSizeFromInvestmentAmount() {
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
        when(orderRepositoryPort.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.placeOrder(command);

        assertEquals(OrderStatus.FILLED, result.status());
        assertEquals(10, result.size());
        assertEquals(new BigDecimal("925.85"), result.price());
    }

    @Test
    @DisplayName("Should reject and persist as REJECTED when user has insufficient cash for BUY order")
    void shouldRejectOrderWhenInsufficientCash() {
        // 100 shares * 925.85 = 92,585.00 ARS > 10,000.00 ARS available
        PlaceOrderCommand command = TradingFixtures.marketBuyBySize(100);

        when(userRepositoryPort.findById(TradingFixtures.DEFAULT_USER_ID))
                .thenReturn(Optional.of(TradingFixtures.defaultUser()));
        when(instrumentRepositoryPort.findById(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampInstrument()));
        when(marketDataRepositoryPort.findLatestByInstrumentId(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampMarketData()));
        when(orderRepositoryPort.findByUserIdAndStatus(TradingFixtures.DEFAULT_USER_ID, OrderStatus.FILLED))
                .thenReturn(List.of(TradingFixtures.cashInOrder(1L, 10_000)));
        when(orderRepositoryPort.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.placeOrder(command);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepositoryPort).save(captor.capture());
        assertEquals(OrderStatus.REJECTED, result.status());
        assertEquals(OrderStatus.REJECTED, captor.getValue().status());
    }

    @Test
    @DisplayName("Should reject and persist as REJECTED when user has insufficient shares for SELL order")
    void shouldRejectOrderWhenInsufficientSharesForSell() {
        PlaceOrderCommand command = new PlaceOrderCommand(
                TradingFixtures.DEFAULT_USER_ID,
                TradingFixtures.PAMP_INSTRUMENT_ID,
                OrderSide.SELL,
                OrderType.MARKET,
                50,
                null,
                null
        );

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
        when(orderRepositoryPort.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.placeOrder(command);

        assertEquals(OrderStatus.REJECTED, result.status());
        assertEquals(50, result.size());
    }

    @Test
    @DisplayName("Should persist LIMIT order with status NEW when buying power is sufficient")
    void shouldPersistLimitOrderAsNew() {
        PlaceOrderCommand command = new PlaceOrderCommand(
                TradingFixtures.DEFAULT_USER_ID,
                TradingFixtures.PAMP_INSTRUMENT_ID,
                OrderSide.BUY,
                OrderType.LIMIT,
                10,
                null,
                new BigDecimal("900.00")
        );

        when(userRepositoryPort.findById(TradingFixtures.DEFAULT_USER_ID))
                .thenReturn(Optional.of(TradingFixtures.defaultUser()));
        when(instrumentRepositoryPort.findById(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampInstrument()));
        when(marketDataRepositoryPort.findLatestByInstrumentId(TradingFixtures.PAMP_INSTRUMENT_ID))
                .thenReturn(Optional.of(TradingFixtures.pampMarketData()));
        when(orderRepositoryPort.findByUserIdAndStatus(TradingFixtures.DEFAULT_USER_ID, OrderStatus.FILLED))
                .thenReturn(List.of(TradingFixtures.cashInOrder(1L, 20_000)));
        when(orderRepositoryPort.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.placeOrder(command);

        assertEquals(OrderStatus.NEW, result.status());
        assertEquals(10, result.size());
        assertEquals(new BigDecimal("900.00"), result.price());
    }

    @Test
    @DisplayName("Should cancel order when status is NEW and reject cancellation when status is FILLED")
    void shouldOnlyCancelOrdersWithNewStatus() {
        Order newOrder = new Order(
                50L,
                TradingFixtures.PAMP_INSTRUMENT_ID,
                TradingFixtures.DEFAULT_USER_ID,
                10,
                new BigDecimal("900.00"),
                OrderType.LIMIT,
                OrderSide.BUY,
                OrderStatus.NEW,
                LocalDateTime.now()
        );
        when(orderRepositoryPort.findById(50L)).thenReturn(Optional.of(newOrder));
        when(orderRepositoryPort.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order cancelled = orderService.cancelOrder(50L);
        assertEquals(OrderStatus.CANCELLED, cancelled.status());

        Order filledOrder = TradingFixtures.filledBuyOrder(51L, TradingFixtures.PAMP_INSTRUMENT_ID, 10, "900.00");
        when(orderRepositoryPort.findById(51L)).thenReturn(Optional.of(filledOrder));

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder(51L));
    }
}

