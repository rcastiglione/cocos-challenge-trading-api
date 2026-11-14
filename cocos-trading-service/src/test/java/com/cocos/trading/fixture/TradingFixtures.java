package com.cocos.trading.fixture;

import com.cocos.trading.application.port.in.model.PlaceOrderCommand;
import com.cocos.trading.domain.model.InstrumentType;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.OrderType;
import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import com.cocos.trading.repository.entity.MarketDataJpaEntity;
import com.cocos.trading.repository.entity.TradeOrderJpaEntity;
import com.cocos.trading.repository.entity.TransferOrderJpaEntity;
import com.cocos.trading.repository.entity.UserJpaEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

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

    public static UserJpaEntity defaultUser() {
        return UserJpaEntity.builder()
                .id(DEFAULT_USER_ID)
                .email("emiliano@test.com")
                .accountNumber("10001")
                .build();
    }

    public static InstrumentJpaEntity arsInstrument() {
        return InstrumentJpaEntity.builder()
                .id(ARS_INSTRUMENT_ID)
                .ticker("ARS")
                .name("PESOS")
                .type(InstrumentType.MONEDA)
                .build();
    }

    public static InstrumentJpaEntity pampInstrument() {
        return InstrumentJpaEntity.builder()
                .id(PAMP_INSTRUMENT_ID)
                .ticker("PAMP")
                .name("Pampa Energía S.A.")
                .type(InstrumentType.ACCIONES)
                .build();
    }

    public static InstrumentJpaEntity metrInstrument() {
        return InstrumentJpaEntity.builder()
                .id(METR_INSTRUMENT_ID)
                .ticker("METR")
                .name("MetroGAS S.A.")
                .type(InstrumentType.ACCIONES)
                .build();
    }

    public static InstrumentJpaEntity instrumentForId(Long instrumentId) {
        if (ARS_INSTRUMENT_ID.equals(instrumentId)) {
            return arsInstrument();
        }
        if (METR_INSTRUMENT_ID.equals(instrumentId)) {
            return metrInstrument();
        }
        return pampInstrument();
    }

    public static MarketDataJpaEntity pampMarketData() {
        return MarketDataJpaEntity.builder()
                .id(100L)
                .instrumentId(PAMP_INSTRUMENT_ID)
                .high(new BigDecimal("950.00"))
                .low(new BigDecimal("900.00"))
                .open(new BigDecimal("910.00"))
                .close(new BigDecimal("925.85"))
                .previousClose(new BigDecimal("906.35"))
                .date(LocalDate.of(2024, 7, 15))
                .build();
    }

    public static MarketDataJpaEntity metrMarketData() {
        return MarketDataJpaEntity.builder()
                .id(101L)
                .instrumentId(METR_INSTRUMENT_ID)
                .high(new BigDecimal("260.00"))
                .low(new BigDecimal("240.00"))
                .open(new BigDecimal("245.00"))
                .close(new BigDecimal("250.00"))
                .previousClose(new BigDecimal("250.00"))
                .date(LocalDate.of(2024, 7, 15))
                .build();
    }

    public static TransferOrderJpaEntity cashInOrder(Long id, int amount) {
        return TransferOrderJpaEntity.builder()
                .id(id)
                .user(defaultUser())
                .instrument(arsInstrument())
                .quantity(amount)
                .price(new BigDecimal("1.00"))
                .type(OrderType.MARKET)
                .side(OrderSide.CASH_IN)
                .status(OrderStatus.FILLED)
                .datetime(LocalDateTime.of(2024, 7, 1, 10, 0))
                .build();
    }

    public static TransferOrderJpaEntity cashOutOrder(Long id, int amount) {
        return TransferOrderJpaEntity.builder()
                .id(id)
                .user(defaultUser())
                .instrument(arsInstrument())
                .quantity(amount)
                .price(new BigDecimal("1.00"))
                .type(OrderType.MARKET)
                .side(OrderSide.CASH_OUT)
                .status(OrderStatus.FILLED)
                .datetime(LocalDateTime.of(2024, 7, 2, 10, 0))
                .build();
    }

    public static TradeOrderJpaEntity filledBuyOrder(Long id, Long instrumentId, int quantity, String price) {
        return TradeOrderJpaEntity.builder()
                .id(id)
                .user(defaultUser())
                .instrument(instrumentForId(instrumentId))
                .quantity(quantity)
                .price(new BigDecimal(price))
                .type(OrderType.MARKET)
                .side(OrderSide.BUY)
                .status(OrderStatus.FILLED)
                .datetime(LocalDateTime.of(2024, 7, 5, 11, 0))
                .build();
    }

    public static TradeOrderJpaEntity filledSellOrder(Long id, Long instrumentId, int quantity, String price) {
        return TradeOrderJpaEntity.builder()
                .id(id)
                .user(defaultUser())
                .instrument(instrumentForId(instrumentId))
                .quantity(quantity)
                .price(new BigDecimal(price))
                .type(OrderType.MARKET)
                .side(OrderSide.SELL)
                .status(OrderStatus.FILLED)
                .datetime(LocalDateTime.of(2024, 7, 6, 12, 0))
                .build();
    }

    public static PlaceOrderCommand marketBuyByQuantity(int quantity) {
        return PlaceOrderCommand.builder()
                .idempotencyKey(UUID.randomUUID())
                .userId(DEFAULT_USER_ID)
                .instrumentId(PAMP_INSTRUMENT_ID)
                .side(OrderSide.BUY)
                .type(OrderType.MARKET)
                .quantity(quantity)
                .build();
    }

    public static PlaceOrderCommand marketBuyByAmount(String amount) {
        return PlaceOrderCommand.builder()
                .idempotencyKey(UUID.randomUUID())
                .userId(DEFAULT_USER_ID)
                .instrumentId(PAMP_INSTRUMENT_ID)
                .side(OrderSide.BUY)
                .type(OrderType.MARKET)
                .amount(MonetaryAmount.ars(amount))
                .build();
    }
}
