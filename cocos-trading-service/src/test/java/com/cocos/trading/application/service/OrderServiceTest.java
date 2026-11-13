package com.cocos.trading.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import com.cocos.trading.domain.model.TradeOrder;
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

        assertInstanceOf(TradeOrder.class, result);
        assertEquals(OrderStatus.FILLED, result.getStatus());
        assertEquals(10, result.getSize());
        assertEquals(new BigDecimal("925.85"), result.getPrice());
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

        assertInstanceOf(TradeOrder.class, result);
        assertEquals(OrderStatus.FILLED, result.getStatus());
        assertEquals(10, result.getSize());
        assertEquals(new BigDecimal("925.85"), result.getPrice());
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
        assertEquals(OrderStatus.REJECTED, result.getStatus());
        assertEquals(OrderStatus.REJECTED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Should reject and persist as REJECTED when user has insufficient shares for SELL order")
    void shouldRejectOrderWhenInsufficientSharesForSell() {
        PlaceOrderCommand command = PlaceOrderCommand.builder()
                .userId(TradingFixtures.DEFAULT_USER_ID)
                .instrumentId(TradingFixtures.PAMP_INSTRUMENT_ID)
                .side(OrderSide.SELL)
                .type(OrderType.MARKET)
                .size(50)
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
        when(orderRepositoryPort.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.placeOrder(command);

        assertEquals(OrderStatus.REJECTED, result.getStatus());
        assertEquals(50, result.getSize());
    }

    @Test
    @DisplayName("Should persist LIMIT order with status NEW when buying power is sufficient")
    void shouldPersistLimitOrderAsNew() {
        PlaceOrderCommand command = PlaceOrderCommand.builder()
                .userId(TradingFixtures.DEFAULT_USER_ID)
                .instrumentId(TradingFixtures.PAMP_INSTRUMENT_ID)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .size(10)
                .price(new BigDecimal("900.00"))
                .build();

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

        assertEquals(OrderStatus.NEW, result.getStatus());
        assertEquals(10, result.getSize());
        assertEquals(new BigDecimal("900.00"), result.getPrice());
    }

    @Test
    @DisplayName("Should cancel order when status is NEW and reject cancellation when status is FILLED")
    void shouldOnlyCancelOrdersWithNewStatus() {
        Order newOrder = TradeOrder.builder()
                .id(50L)
                .instrumentId(TradingFixtures.PAMP_INSTRUMENT_ID)
                .userId(TradingFixtures.DEFAULT_USER_ID)
                .size(10)
                .price(new BigDecimal("900.00"))
                .type(OrderType.LIMIT)
                .side(OrderSide.BUY)
                .status(OrderStatus.NEW)
                .datetime(LocalDateTime.now())
                .build();
        when(orderRepositoryPort.findById(50L)).thenReturn(Optional.of(newOrder));
        when(orderRepositoryPort.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order cancelled = orderService.cancelOrder(50L);
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());

        Order filledOrder = TradingFixtures.filledBuyOrder(51L, TradingFixtures.PAMP_INSTRUMENT_ID, 10, "900.00");
        when(orderRepositoryPort.findById(51L)).thenReturn(Optional.of(filledOrder));

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder(51L));
    }
}
