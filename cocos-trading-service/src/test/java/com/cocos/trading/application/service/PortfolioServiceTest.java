package com.cocos.trading.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.cocos.trading.application.port.out.MarketDataRepositoryPort;
import com.cocos.trading.application.port.out.OrderRepositoryPort;
import com.cocos.trading.application.port.out.UserRepositoryPort;
import com.cocos.trading.domain.exception.ResourceNotFoundException;
import com.cocos.trading.domain.model.AssetPosition;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.Portfolio;
import com.cocos.trading.fixture.TradingFixtures;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Portfolio Service Unit Tests")
class PortfolioServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private OrderRepositoryPort orderRepositoryPort;

    @Mock
    private MarketDataRepositoryPort marketDataRepositoryPort;

    @InjectMocks
    private PortfolioService portfolioService;

    @Test
    @DisplayName("Should calculate total account value, available cash, and position returns from FILLED orders")
    void shouldCalculatePortfolioFromFilledOrders() {
        // Given:
        // CASH_IN: +1,000,000 ARS
        // CASH_OUT: -100,000 ARS
        // BUY 100 PAMP @ 800.00 = -80,000 ARS
        // SELL 20 PAMP @ 900.00 = +18,000 ARS
        // Net Cash = 1,000,000 - 100,000 - 80,000 + 18,000 = 838,000.00 ARS
        // Net PAMP shares = 80, average cost = 800.00 ARS
        // Latest PAMP price = 925.85 -> Market Value = 80 * 925.85 = 74,068.00 ARS
        // Total Return % = ((925.85 - 800.00) / 800.00) * 100 = 15.73%
        // Total Account Value = 838,000.00 + 74,068.00 = 912,068.00 ARS
        Long userId = TradingFixtures.DEFAULT_USER_ID;
        List<OrderJpaEntity> filledOrders = List.of(
                TradingFixtures.cashInOrder(1L, 1_000_000),
                TradingFixtures.cashOutOrder(2L, 100_000),
                TradingFixtures.filledBuyOrder(3L, TradingFixtures.PAMP_INSTRUMENT_ID, 100, "800.00"),
                TradingFixtures.filledSellOrder(4L, TradingFixtures.PAMP_INSTRUMENT_ID, 20, "900.00")
        );

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(TradingFixtures.defaultUser()));
        when(orderRepositoryPort.findByUserIdAndStatus(userId, OrderStatus.FILLED)).thenReturn(filledOrders);
        when(marketDataRepositoryPort.findLatestByInstrumentIds(Set.of(TradingFixtures.PAMP_INSTRUMENT_ID)))
                .thenReturn(Map.of(TradingFixtures.PAMP_INSTRUMENT_ID, TradingFixtures.pampMarketData()));

        // When
        Portfolio portfolio = portfolioService.getPortfolio(userId);

        // Then
        assertEquals(userId, portfolio.user().getId());
        assertEquals("10001", portfolio.user().getAccountNumber());
        assertEquals(MonetaryAmount.ars("838000.00"), portfolio.availableCash());
        assertEquals(MonetaryAmount.ars("912068.00"), portfolio.totalAccountValue());
        assertEquals(1, portfolio.positions().size());

        AssetPosition pampPosition = portfolio.positions().getFirst();
        assertEquals("PAMP", pampPosition.instrument().getTicker());
        assertEquals(80, pampPosition.quantity());
        assertEquals(MonetaryAmount.ars("925.85"), pampPosition.lastPrice());
        assertEquals(MonetaryAmount.ars("74068.00"), pampPosition.marketValue());
        assertEquals(new BigDecimal("15.73"), pampPosition.totalReturnPercentage());
        assertEquals(new BigDecimal("2.15"), pampPosition.dailyReturnPercentage());
    }

    @Test
    @DisplayName("Should exclude closed positions where net share quantity is zero")
    void shouldExcludeClosedPositions() {
        Long userId = TradingFixtures.DEFAULT_USER_ID;
        List<OrderJpaEntity> filledOrders = List.of(
                TradingFixtures.cashInOrder(1L, 100_000),
                TradingFixtures.filledBuyOrder(2L, TradingFixtures.PAMP_INSTRUMENT_ID, 10, "800.00"),
                TradingFixtures.filledSellOrder(3L, TradingFixtures.PAMP_INSTRUMENT_ID, 10, "850.00")
        );

        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(TradingFixtures.defaultUser()));
        when(orderRepositoryPort.findByUserIdAndStatus(userId, OrderStatus.FILLED)).thenReturn(filledOrders);
        when(marketDataRepositoryPort.findLatestByInstrumentIds(Set.of(TradingFixtures.PAMP_INSTRUMENT_ID)))
                .thenReturn(Map.of(TradingFixtures.PAMP_INSTRUMENT_ID, TradingFixtures.pampMarketData()));

        Portfolio portfolio = portfolioService.getPortfolio(userId);

        assertEquals(MonetaryAmount.ars("100500.00"), portfolio.availableCash());
        assertEquals(MonetaryAmount.ars("100500.00"), portfolio.totalAccountValue());
        assertTrue(portfolio.positions().isEmpty());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user does not exist")
    void shouldThrowResourceNotFoundWhenUserMissing() {
        when(userRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> portfolioService.getPortfolio(999L));
    }
}
