package com.cocos.trading.fixture;

import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.domain.model.Instrument;
import com.cocos.trading.domain.model.InstrumentType;
import com.cocos.trading.domain.model.MarketData;
import com.cocos.trading.domain.model.Order;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.OrderType;
import com.cocos.trading.domain.model.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Reusable test fixtures for unit and integration tests.
 */
public final class TradingFixtures {

    public static final Long DEFAULT_USER_ID = 1L;
    public static final Long ARS_INSTRUMENT_ID = 66L;
    public static final Long PAMP_INSTRUMENT_ID = 47L;
    public static final Long METR_INSTRUMENT_ID = 31L;

    private TradingFixtures() {
    }

    public static User defaultUser() {
        return new User(DEFAULT_USER_ID, "emiliano@test.com", "10001");
    }

    public static Instrument arsInstrument() {
        return new Instrument(ARS_INSTRUMENT_ID, "ARS", "PESOS", InstrumentType.MONEDA);
    }

    public static Instrument pampInstrument() {
        return new Instrument(PAMP_INSTRUMENT_ID, "PAMP", "Pampa Energía S.A.", InstrumentType.ACCIONES);
    }

    public static Instrument metrInstrument() {
        return new Instrument(METR_INSTRUMENT_ID, "METR", "MetroGAS S.A.", InstrumentType.ACCIONES);
    }

    public static MarketData pampMarketData() {
        return new MarketData(
                100L,
                PAMP_INSTRUMENT_ID,
                new BigDecimal("950.00"),
                new BigDecimal("900.00"),
                new BigDecimal("910.00"),
                new BigDecimal("925.85"),
                new BigDecimal("906.35"),
                LocalDate.of(2024, 7, 15)
        );
    }

    public static MarketData metrMarketData() {
        return new MarketData(
                101L,
                METR_INSTRUMENT_ID,
                new BigDecimal("260.00"),
                new BigDecimal("240.00"),
                new BigDecimal("245.00"),
                new BigDecimal("250.00"),
                new BigDecimal("250.00"),
                LocalDate.of(2024, 7, 15)
        );
    }

    public static Order cashInOrder(Long id, int amount) {
        return new Order(
                id,
                ARS_INSTRUMENT_ID,
                DEFAULT_USER_ID,
                amount,
                new BigDecimal("1.00"),
                OrderType.MARKET,
                OrderSide.CASH_IN,
                OrderStatus.FILLED,
                LocalDateTime.of(2024, 7, 1, 10, 0)
        );
    }

    public static Order cashOutOrder(Long id, int amount) {
        return new Order(
                id,
                ARS_INSTRUMENT_ID,
                DEFAULT_USER_ID,
                amount,
                new BigDecimal("1.00"),
                OrderType.MARKET,
                OrderSide.CASH_OUT,
                OrderStatus.FILLED,
                LocalDateTime.of(2024, 7, 2, 10, 0)
        );
    }

    public static Order filledBuyOrder(Long id, Long instrumentId, int size, String price) {
        return new Order(
                id,
                instrumentId,
                DEFAULT_USER_ID,
                size,
                new BigDecimal(price),
                OrderType.MARKET,
                OrderSide.BUY,
                OrderStatus.FILLED,
                LocalDateTime.of(2024, 7, 5, 11, 0)
        );
    }

    public static Order filledSellOrder(Long id, Long instrumentId, int size, String price) {
        return new Order(
                id,
                instrumentId,
                DEFAULT_USER_ID,
                size,
                new BigDecimal(price),
                OrderType.MARKET,
                OrderSide.SELL,
                OrderStatus.FILLED,
                LocalDateTime.of(2024, 7, 6, 12, 0)
        );
    }

    public static PlaceOrderCommand marketBuyBySize(int size) {
        return new PlaceOrderCommand(
                DEFAULT_USER_ID,
                PAMP_INSTRUMENT_ID,
                OrderSide.BUY,
                OrderType.MARKET,
                size,
                null,
                null
        );
    }

    public static PlaceOrderCommand marketBuyByAmount(String amount) {
        return new PlaceOrderCommand(
                DEFAULT_USER_ID,
                PAMP_INSTRUMENT_ID,
                OrderSide.BUY,
                OrderType.MARKET,
                null,
                new BigDecimal(amount),
                null
        );
    }
}

