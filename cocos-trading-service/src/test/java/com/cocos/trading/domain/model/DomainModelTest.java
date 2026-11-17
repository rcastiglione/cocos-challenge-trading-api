package com.cocos.trading.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cocos.trading.api.response.TradeOrderApi;
import com.cocos.trading.api.response.TransferOrderApi;
import com.cocos.trading.application.service.AccountLedgerCalculator;
import com.cocos.trading.controller.mapper.ApiMapper;
import com.cocos.trading.domain.exception.BusinessRuleViolationException;
import com.cocos.trading.fixture.TradingFixtures;
import com.cocos.trading.repository.entity.MarketDataJpaEntity;
import com.cocos.trading.repository.entity.TradeOrderJpaEntity;
import com.cocos.trading.repository.entity.TransferOrderJpaEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Value Objects, Ledger Calculations & Direct JPA Entity Mapping Unit Tests")
class DomainModelTest {

    @Test
    @DisplayName("Should calculate maximum whole shares from investment amount without fractions via AccountLedgerCalculator")
    void shouldCalculateMaxShareQuantityWithoutFractions() {
        // 10,000 ARS / 925.85 ARS per share = 10.8008... -> 10 whole shares
        long shares = AccountLedgerCalculator.calculateMaxShareQuantity(
                MonetaryAmount.ars("10000.00"),
                MonetaryAmount.ars("925.85")
        );
        assertEquals(10L, shares);
    }

    @Test
    @DisplayName("Should return 0 shares when investment amount is smaller than unit price")
    void shouldReturnZeroSharesWhenAmountLessThanUnitPrice() {
        long shares = AccountLedgerCalculator.calculateMaxShareQuantity(
                MonetaryAmount.ars("500.00"),
                MonetaryAmount.ars("925.85")
        );
        assertEquals(0L, shares);
    }

    @Test
    @DisplayName("Should calculate daily return percentage accurately from close and previousClose via AccountLedgerCalculator")
    void shouldCalculateDailyReturnPercentage() {
        MarketDataJpaEntity marketData = MarketDataJpaEntity.builder()
                .id(1L)
                .instrumentId(47L)
                .high(new BigDecimal("950.00"))
                .low(new BigDecimal("900.00"))
                .open(new BigDecimal("910.00"))
                .close(new BigDecimal("925.85"))
                .previousClose(new BigDecimal("906.35"))
                .date(LocalDate.of(2024, 7, 15))
                .build();

        // ((925.85 - 906.35) / 906.35) * 100 = 2.151486... -> 2.15%
        assertEquals(new BigDecimal("2.15"), AccountLedgerCalculator.calculateDailyReturnPercentage(marketData));
    }

    @Test
    @DisplayName("Should compute total amount and signed cash impact directly on TradeOrderJpaEntity and TransferOrderJpaEntity")
    void shouldComputeLedgerImpactsOnOrderJpaEntities() {
        TradeOrderJpaEntity buyOrder = TradeOrderJpaEntity.builder()
                .id(10L)
                .user(TradingFixtures.defaultUser())
                .instrument(TradingFixtures.pampInstrument())
                .quantity(5L)
                .price(new BigDecimal("900.00"))
                .type(OrderType.LIMIT)
                .side(OrderSide.BUY)
                .status(OrderStatus.FILLED)
                .datetime(LocalDateTime.now())
                .build();

        TransferOrderJpaEntity cashInOrder = TransferOrderJpaEntity.builder()
                .id(11L)
                .user(TradingFixtures.defaultUser())
                .instrument(TradingFixtures.arsInstrument())
                .quantity(50000L)
                .price(new BigDecimal("1.00"))
                .type(OrderType.MARKET)
                .side(OrderSide.CASH_IN)
                .status(OrderStatus.FILLED)
                .datetime(LocalDateTime.now())
                .build();

        assertEquals(MonetaryAmount.ars("4500.00"), AccountLedgerCalculator.calculateTotalAmount(buyOrder));
        assertEquals(MonetaryAmount.ars("-4500.00"), AccountLedgerCalculator.calculateCashImpact(buyOrder));
        assertEquals(MonetaryAmount.ars("50000.00"), AccountLedgerCalculator.calculateCashImpact(cashInOrder));
    }

    @Test
    @DisplayName("ApiMapper should map TradeOrderJpaEntity to TradeOrderApi (quantity/price) and TransferOrderJpaEntity to TransferOrderApi (amount)")
    void shouldMapOrderJpaEntityDirectlyToOrderApi() {
        TradeOrderJpaEntity tradeOrderEntity = TradeOrderJpaEntity.builder()
                .id(105L)
                .user(TradingFixtures.defaultUser())
                .instrument(TradingFixtures.pampInstrument())
                .quantity(10L)
                .price(new BigDecimal("925.85"))
                .type(OrderType.MARKET)
                .side(OrderSide.BUY)
                .status(OrderStatus.FILLED)
                .datetime(LocalDateTime.of(2024, 6, 15, 14, 30, 0))
                .build();

        TradeOrderApi tradeOrderApi = assertInstanceOf(TradeOrderApi.class, ApiMapper.toApi(tradeOrderEntity));

        assertEquals(105L, tradeOrderApi.id());
        assertEquals(1L, tradeOrderApi.user().id());
        assertEquals("10001", tradeOrderApi.user().accountNumber());
        assertEquals(47L, tradeOrderApi.instrument().id());
        assertEquals("PAMP", tradeOrderApi.instrument().ticker());
        assertEquals(10L, tradeOrderApi.quantity());
        assertEquals(new BigDecimal("925.85"), tradeOrderApi.price().amount());
        assertEquals("ARS", tradeOrderApi.price().currency());
        assertEquals("2024-06-15T14:30:00Z", tradeOrderApi.datetime());

        TransferOrderJpaEntity transferOrderEntity = TransferOrderJpaEntity.builder()
                .id(106L)
                .user(TradingFixtures.defaultUser())
                .instrument(TradingFixtures.arsInstrument())
                .quantity(50_000L)
                .price(new BigDecimal("1.00"))
                .type(OrderType.MARKET)
                .side(OrderSide.CASH_IN)
                .status(OrderStatus.FILLED)
                .datetime(LocalDateTime.of(2024, 6, 15, 14, 35, 0))
                .build();

        TransferOrderApi transferOrderApi = assertInstanceOf(TransferOrderApi.class, ApiMapper.toApi(transferOrderEntity));

        assertEquals(106L, transferOrderApi.id());
        assertEquals(1L, transferOrderApi.user().id());
        assertEquals("10001", transferOrderApi.user().accountNumber());
        assertEquals(new BigDecimal("50000.00"), transferOrderApi.amount().amount());
        assertEquals("ARS", transferOrderApi.amount().currency());
        assertEquals(com.cocos.trading.api.common.OrderStatusApi.FILLED, transferOrderApi.status());
        assertEquals("2024-06-15T14:35:00Z", transferOrderApi.datetime());
    }

    @Test
    @DisplayName("MonetaryAmount Value Object should enforce currency safety and scale normalization")
    void shouldEnforceMonetaryAmountCurrencySafetyAndScale() {
        MonetaryAmount arsAmount = MonetaryAmount.of(new BigDecimal("100.5"), "ars");
        MonetaryAmount usdAmount = MonetaryAmount.of(new BigDecimal("50.00"), "USD");

        assertEquals(new BigDecimal("100.50"), arsAmount.amount());
        assertEquals("ARS", arsAmount.currency());
        assertTrue(arsAmount.isGreaterThan(MonetaryAmount.ars("50.00")));

        assertThrows(BusinessRuleViolationException.class, () -> arsAmount.add(usdAmount));
        assertThrows(BusinessRuleViolationException.class, () -> arsAmount.subtract(usdAmount));
        assertThrows(BusinessRuleViolationException.class, () -> arsAmount.isLessThan(usdAmount));
    }
}
