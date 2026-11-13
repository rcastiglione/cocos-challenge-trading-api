package com.cocos.trading.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocos.trading.TradingApplicationIT;
import com.cocos.trading.domain.model.InstrumentType;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.OrderType;
import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import com.cocos.trading.repository.entity.MarketDataJpaEntity;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import com.cocos.trading.repository.entity.UserJpaEntity;
import com.cocos.trading.repository.jpa.SpringDataInstrumentRepository;
import com.cocos.trading.repository.jpa.SpringDataMarketDataRepository;
import com.cocos.trading.repository.jpa.SpringDataOrderRepository;
import com.cocos.trading.repository.jpa.SpringDataUserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Functional integration tests for the {@code GET /portfolios/{userId}} and {@code GET /instruments} endpoints.
 */
@DisplayName("Portfolio and Instrument Controllers Functional Integration Tests")
class PortfolioAndInstrumentControllerIT extends TradingApplicationIT {

    @Autowired
    private SpringDataUserRepository userRepository;

    @Autowired
    private SpringDataInstrumentRepository instrumentRepository;

    @Autowired
    private SpringDataMarketDataRepository marketDataRepository;

    @Autowired
    private SpringDataOrderRepository orderRepository;

    private UserJpaEntity savedUser;
    private InstrumentJpaEntity pampInstrument;

    @BeforeEach
    void setUpDatabase() {
        orderRepository.deleteAll();
        marketDataRepository.deleteAll();
        instrumentRepository.deleteAll();
        userRepository.deleteAll();

        savedUser = userRepository.save(new UserJpaEntity(null, "emiliano@test.com", "10001"));
        InstrumentJpaEntity arsInstrument = instrumentRepository.save(
                new InstrumentJpaEntity(null, "ARS", "PESOS", InstrumentType.MONEDA)
        );
        pampInstrument = instrumentRepository.save(
                new InstrumentJpaEntity(null, "PAMP", "Pampa Energía S.A.", InstrumentType.ACCIONES)
        );
        instrumentRepository.save(
                new InstrumentJpaEntity(null, "MOLI", "Molinos Río de la Plata", InstrumentType.ACCIONES)
        );

        marketDataRepository.save(new MarketDataJpaEntity(
                null,
                pampInstrument.getId(),
                new BigDecimal("950.00"),
                new BigDecimal("900.00"),
                new BigDecimal("910.00"),
                new BigDecimal("925.85"),
                new BigDecimal("906.35"),
                LocalDate.of(2024, 7, 15)
        ));

        orderRepository.save(new OrderJpaEntity(
                null,
                arsInstrument.getId(),
                savedUser.getId(),
                100_000,
                new BigDecimal("1.00"),
                OrderType.MARKET,
                OrderSide.CASH_IN,
                OrderStatus.FILLED,
                LocalDateTime.of(2024, 7, 1, 10, 0)
        ));

        orderRepository.save(new OrderJpaEntity(
                null,
                pampInstrument.getId(),
                savedUser.getId(),
                10,
                new BigDecimal("800.00"),
                OrderType.MARKET,
                OrderSide.BUY,
                OrderStatus.FILLED,
                LocalDateTime.of(2024, 7, 2, 11, 0)
        ));
    }

    @Test
    @DisplayName("Should return user portfolio with available cash, positions, and total valuation")
    void shouldGetPortfolioValuation() throws Exception {
        // Available Cash = 100,000 - (10 * 800) = 92,000.00
        // Position PAMP = 10 * 925.85 = 9,258.50 (Total Return = (925.85 - 800)/800 * 100 = 15.73%)
        // Total Account Value = 92,000.00 + 9,258.50 = 101,258.50
        mockMvc.perform(get("/portfolios/{userId}", savedUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(savedUser.getId()))
                .andExpect(jsonPath("$.accountNumber").value("10001"))
                .andExpect(jsonPath("$.availableCash").value(92000.00))
                .andExpect(jsonPath("$.totalAccountValue").value(101258.50))
                .andExpect(jsonPath("$.positions.length()").value(1))
                .andExpect(jsonPath("$.positions[0].ticker").value("PAMP"))
                .andExpect(jsonPath("$.positions[0].quantity").value(10))
                .andExpect(jsonPath("$.positions[0].marketValue").value(9258.50))
                .andExpect(jsonPath("$.positions[0].totalReturnPercentage").value(15.73));
    }

    @Test
    @DisplayName("Should search instruments by query, ticker, and name")
    void shouldSearchInstrumentsByTickerAndName() throws Exception {
        mockMvc.perform(get("/instruments").param("query", "pampa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ticker").value("PAMP"))
                .andExpect(jsonPath("$[0].lastPrice").value(925.85));

        mockMvc.perform(get("/instruments").param("ticker", "MOLI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Molinos Río de la Plata"));
    }
}

