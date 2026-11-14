package com.cocos.trading.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cocos.trading.domain.exception.BusinessRuleViolationException;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import com.cocos.trading.repository.mapper.PersistenceMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Domain Models, Value Objects & Polymorphism Unit Tests")
class DomainModelTest {

    @Test
    @DisplayName("Should calculate maximum whole shares from investment amount without fractions")
    void shouldCalculateMaxShareSizeWithoutFractions() {
        // 10,000 ARS / 925.85 ARS per share = 10.8008... -> 10 whole shares
        int shares = Order.calculateMaxShareSize(MonetaryAmount.ars("10000.00"), MonetaryAmount.ars("925.85"));
        assertEquals(10, shares);
    }

    @Test
    @DisplayName("Should return 0 shares when investment amount is smaller than unit price")
    void shouldReturnZeroSharesWhenAmountLessThanUnitPrice() {
        int shares = Order.calculateMaxShareSize(MonetaryAmount.ars("500.00"), MonetaryAmount.ars("925.85"));
        assertEquals(0, shares);
    }

    @Test
    @DisplayName("Should calculate daily return percentage accurately from close and previousClose")
    void shouldCalculateDailyReturnPercentage() {
        MarketData marketData = MarketData.builder()
                .id(1L)
                .instrumentId(47L)
                .high(MonetaryAmount.ars("950.00"))
                .low(MonetaryAmount.ars("900.00"))
                .open(MonetaryAmount.ars("910.00"))
                .close(MonetaryAmount.ars("925.85"))
                .previousClose(MonetaryAmount.ars("906.35"))
                .date(LocalDate.of(2024, 7, 15))
                .build();

        // ((925.85 - 906.35) / 906.35) * 100 = 2.151486... -> 2.15%
        assertEquals(new BigDecimal("2.15"), marketData.calculateDailyReturnPercentage());
    }

    @Test
    @DisplayName("Should transition NEW TradeOrder to CANCELLED preserving TradeOrder type")
    void shouldTransitionTradeOrderToCancelled() {
        TradeOrder newOrder = TradeOrder.builder()
                .id(10L)
                .instrumentId(47L)
                .userId(1L)
                .size(5)
                .price(MonetaryAmount.ars("900.00"))
                .type(OrderType.LIMIT)
                .side(OrderSide.BUY)
                .status(OrderStatus.NEW)
                .datetime(LocalDateTime.now())
                .build();

        Order cancelled = newOrder.cancel();
        assertInstanceOf(TradeOrder.class, cancelled);
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals(MonetaryAmount.ars("4500.00"), cancelled.getTotalAmount());
        assertEquals(MonetaryAmount.ars("-4500.00"), cancelled.getCashImpact());
    }

    @Test
    @DisplayName("Should enforce TradeOrder and TransferOrder side invariants")
    void shouldEnforceOrderSubclassSideInvariants() {
        assertThrows(BusinessRuleViolationException.class, () -> TradeOrder.builder()
                .instrumentId(66L)
                .userId(1L)
                .size(1000)
                .price(MonetaryAmount.ONE_ARS)
                .type(OrderType.MARKET)
                .side(OrderSide.CASH_IN)
                .status(OrderStatus.FILLED)
                .build());

        assertThrows(BusinessRuleViolationException.class, () -> TransferOrder.builder()
                .instrumentId(47L)
                .userId(1L)
                .size(10)
                .price(MonetaryAmount.ars("900.00"))
                .type(OrderType.MARKET)
                .side(OrderSide.BUY)
                .status(OrderStatus.FILLED)
                .build());
    }

    @Test
    @DisplayName("PersistenceMapper should discriminate OrderJpaEntity into TradeOrder or TransferOrder and wrap ARS MonetaryAmount")
    void shouldDiscriminateEntityIntoCorrectOrderSubclass() {
        OrderJpaEntity tradeEntity = OrderJpaEntity.builder()
                .id(1L)
                .instrumentId(47L)
                .userId(1L)
                .size(10)
                .price(new BigDecimal("925.85"))
                .type(OrderType.MARKET)
                .side(OrderSide.BUY)
                .status(OrderStatus.FILLED)
                .datetime(LocalDateTime.now())
                .build();

        OrderJpaEntity transferEntity = OrderJpaEntity.builder()
                .id(2L)
                .instrumentId(66L)
                .userId(1L)
                .size(50000)
                .price(new BigDecimal("1.00"))
                .type(OrderType.MARKET)
                .side(OrderSide.CASH_IN)
                .status(OrderStatus.FILLED)
                .datetime(LocalDateTime.now())
                .build();

        Order mappedTrade = PersistenceMapper.toDomain(tradeEntity);
        Order mappedTransfer = PersistenceMapper.toDomain(transferEntity);

        assertInstanceOf(TradeOrder.class, mappedTrade);
        assertInstanceOf(TransferOrder.class, mappedTransfer);
        assertEquals(MonetaryAmount.ars("50000.00"), mappedTransfer.getCashImpact());
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
