package com.cocos.trading.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Domain Models Unit Tests")
class DomainModelTest {

    @Test
    @DisplayName("Should calculate maximum whole shares from investment amount without fractions")
    void shouldCalculateMaxShareSizeWithoutFractions() {
        // 10,000 ARS / 925.85 ARS per share = 10.8008... -> 10 whole shares
        int shares = Order.calculateMaxShareSize(new BigDecimal("10000.00"), new BigDecimal("925.85"));
        assertEquals(10, shares);
    }

    @Test
    @DisplayName("Should return 0 shares when investment amount is smaller than unit price")
    void shouldReturnZeroSharesWhenAmountLessThanUnitPrice() {
        int shares = Order.calculateMaxShareSize(new BigDecimal("500.00"), new BigDecimal("925.85"));
        assertEquals(0, shares);
    }

    @Test
    @DisplayName("Should calculate daily return percentage accurately from close and previousClose")
    void shouldCalculateDailyReturnPercentage() {
        MarketData marketData = new MarketData(
                1L,
                47L,
                new BigDecimal("950.00"),
                new BigDecimal("900.00"),
                new BigDecimal("910.00"),
                new BigDecimal("925.85"),
                new BigDecimal("906.35"),
                LocalDate.of(2024, 7, 15)
        );

        // ((925.85 - 906.35) / 906.35) * 100 = 2.151486... -> 2.15%
        assertEquals(new BigDecimal("2.15"), marketData.calculateDailyReturnPercentage());
    }

    @Test
    @DisplayName("Should transition NEW order to CANCELLED")
    void shouldTransitionOrderToCancelled() {
        Order newOrder = new Order(
                10L,
                47L,
                1L,
                5,
                new BigDecimal("900.00"),
                OrderType.LIMIT,
                OrderSide.BUY,
                OrderStatus.NEW,
                LocalDateTime.now()
        );

        Order cancelled = newOrder.cancel();
        assertEquals(OrderStatus.CANCELLED, cancelled.status());
        assertEquals(new BigDecimal("4500.00"), cancelled.getTotalAmount());
    }
}

