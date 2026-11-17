package com.cocos.trading.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocos.trading.TradingApplicationIT;
import com.cocos.trading.api.OrderContractApi;
import com.cocos.trading.api.common.OrderSideApi;
import com.cocos.trading.api.common.OrderTypeApi;
import com.cocos.trading.api.request.OrderCreateRequestApi;
import com.cocos.trading.api.request.TradeOrderRequestApi;
import com.cocos.trading.api.request.TransferOrderRequestApi;
import com.cocos.trading.api.response.AssetPositionApi;
import com.cocos.trading.api.response.PortfolioApi;
import com.cocos.trading.domain.model.InstrumentType;
import com.cocos.trading.domain.model.MonetaryAmount;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.OrderType;
import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import com.cocos.trading.repository.entity.MarketDataJpaEntity;
import com.cocos.trading.repository.entity.TransferOrderJpaEntity;
import com.cocos.trading.repository.entity.UserJpaEntity;
import com.cocos.trading.repository.jpa.SpringDataInstrumentRepository;
import com.cocos.trading.repository.jpa.SpringDataMarketDataRepository;
import com.cocos.trading.repository.jpa.SpringDataOrderRepository;
import com.cocos.trading.repository.jpa.SpringDataUserRepository;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end transactional lifecycle integration tests verifying that
 * {@code POST /orders} mutations are immediately and accurately reflected by
 * {@code GET /portfolios/{userId}} using {@link MonetaryAmount}.
 */
@DisplayName("Portfolio Lifecycle Functional Integration Tests")
class PortfolioLifecycleIT extends TradingApplicationIT {

    private static final MonetaryAmount INITIAL_CASH = MonetaryAmount.ars("100000.00");
    private static final MonetaryAmount PAMP_CLOSE_PRICE = MonetaryAmount.ars("1000.00");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SpringDataUserRepository userRepository;

    @Autowired
    private SpringDataInstrumentRepository instrumentRepository;

    @Autowired
    private SpringDataMarketDataRepository marketDataRepository;

    @Autowired
    private SpringDataOrderRepository orderRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private UserJpaEntity savedUser;
    private InstrumentJpaEntity pampInstrument;

    @BeforeEach
    void setUpDatabase() {
        var connectionFactory = stringRedisTemplate.getConnectionFactory();
        if (connectionFactory != null) {
            try (var connection = connectionFactory.getConnection()) {
                connection.serverCommands().flushAll();
            }
        }
        orderRepository.deleteAll();
        marketDataRepository.deleteAll();
        instrumentRepository.deleteAll();
        userRepository.deleteAll();

        savedUser = userRepository.save(new UserJpaEntity(null, "emiliano@test.com", "10001"));
        InstrumentJpaEntity arsInstrument = instrumentRepository.save(
                new InstrumentJpaEntity(null, "ARS", "PESOS", InstrumentType.MONEDA));
        pampInstrument = instrumentRepository.save(
                new InstrumentJpaEntity(null, "PAMP", "Pampa Energía S.A.", InstrumentType.ACCIONES));

        marketDataRepository.save(new MarketDataJpaEntity(
                null,
                pampInstrument.getId(),
                new BigDecimal("1020.00"),
                new BigDecimal("980.00"),
                new BigDecimal("990.00"),
                PAMP_CLOSE_PRICE.amount(),
                new BigDecimal("950.00"),
                LocalDate.of(2024, 7, 15)));

        orderRepository.save(TransferOrderJpaEntity.builder()
                .instrument(arsInstrument)
                .user(savedUser)
                .quantity(100_000)
                .price(new BigDecimal("1.00"))
                .type(OrderType.MARKET)
                .side(OrderSide.CASH_IN)
                .status(OrderStatus.FILLED)
                .datetime(LocalDateTime.of(2024, 7, 1, 10, 0))
                .build());
    }

    @Test
    @DisplayName("Scenario A: Check Portfolio -> Market Buy -> Check Portfolio Again updates availableCash, position quantity, and preserves totalAccountValue")
    void scenarioA_marketBuyUpdatesPortfolio() throws Exception {
        // 1. Check Portfolio (Before)
        PortfolioApi initialPortfolio = fetchPortfolio(savedUser.getId());
        assertEquals(INITIAL_CASH, toDomain(initialPortfolio.availableCash()));
        assertEquals(INITIAL_CASH, toDomain(initialPortfolio.totalAccountValue()));
        assertTrue(initialPortfolio.positions().isEmpty());

        // 2. Execute MARKET BUY
        int buyQuantity = 10;
        MonetaryAmount expectedCost = PAMP_CLOSE_PRICE.multiply(buyQuantity);
        MonetaryAmount expectedCash = INITIAL_CASH.subtract(expectedCost);

        OrderCreateRequestApi buyRequest = new TradeOrderRequestApi(
                savedUser.getId(),
                pampInstrument.getId(),
                OrderSideApi.BUY,
                OrderTypeApi.MARKET,
                buyQuantity,
                null,
                null);

        mockMvc.perform(post("/orders")
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buyRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.price.amount").value(1000.00));

        // 3. Check Portfolio Again (After)
        PortfolioApi updatedPortfolio = fetchPortfolio(savedUser.getId());
        assertEquals(expectedCash, toDomain(updatedPortfolio.availableCash()));
        assertEquals(INITIAL_CASH, toDomain(updatedPortfolio.totalAccountValue()));
        assertEquals(1, updatedPortfolio.positions().size());

        AssetPositionApi position = updatedPortfolio.positions().getFirst();
        assertEquals(pampInstrument.getId(), position.instrument().id());
        assertEquals("PAMP", position.instrument().ticker());
        assertEquals(buyQuantity, position.quantity());
        assertEquals(expectedCost, toDomain(position.marketValue()));
    }

    @Test
    @DisplayName("Scenario B: Market Sell decreases position quantity, increases availableCash proportionally, and keeps totalAccountValue consistent")
    void scenarioB_marketSellUpdatesPortfolio() throws Exception {
        int buyQuantity = 10;
        int sellQuantity = 4;
        int remainingQuantity = buyQuantity - sellQuantity;

        OrderCreateRequestApi buyRequest = new TradeOrderRequestApi(
                savedUser.getId(),
                pampInstrument.getId(),
                OrderSideApi.BUY,
                OrderTypeApi.MARKET,
                buyQuantity,
                null,
                null);

        mockMvc.perform(post("/orders")
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buyRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"));

        OrderCreateRequestApi sellRequest = new TradeOrderRequestApi(
                savedUser.getId(),
                pampInstrument.getId(),
                OrderSideApi.SELL,
                OrderTypeApi.MARKET,
                sellQuantity,
                null,
                null);

        mockMvc.perform(post("/orders")
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sellRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"));

        MonetaryAmount expectedCash = INITIAL_CASH
                .subtract(PAMP_CLOSE_PRICE.multiply(buyQuantity))
                .add(PAMP_CLOSE_PRICE.multiply(sellQuantity));
        MonetaryAmount expectedPositionValue = PAMP_CLOSE_PRICE.multiply(remainingQuantity);

        PortfolioApi portfolio = fetchPortfolio(savedUser.getId());
        assertEquals(expectedCash, toDomain(portfolio.availableCash()));
        assertEquals(INITIAL_CASH, toDomain(portfolio.totalAccountValue()));
        assertEquals(1, portfolio.positions().size());

        AssetPositionApi position = portfolio.positions().getFirst();
        assertEquals(remainingQuantity, position.quantity());
        assertEquals(expectedPositionValue, toDomain(position.marketValue()));
    }

    @Test
    @DisplayName("Scenario C: Cash-In (Deposit) increases both availableCash and totalAccountValue by exact amount")
    void scenarioC_cashInIncreasesBalances() throws Exception {
        MonetaryAmount deposit = MonetaryAmount.ars("25000.00");
        MonetaryAmount expectedTotal = INITIAL_CASH.add(deposit);

        OrderCreateRequestApi cashInRequest = new TransferOrderRequestApi(
                savedUser.getId(),
                OrderSideApi.CASH_IN,
                toApi(deposit));

        mockMvc.perform(post("/orders")
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cashInRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"));

        PortfolioApi portfolio = fetchPortfolio(savedUser.getId());
        assertEquals(expectedTotal, toDomain(portfolio.availableCash()));
        assertEquals(expectedTotal, toDomain(portfolio.totalAccountValue()));
        assertTrue(portfolio.positions().isEmpty());
    }

    @Test
    @DisplayName("Scenario D: Cash-Out (Withdrawal) decreases both availableCash and totalAccountValue by exact amount")
    void scenarioD_cashOutDecreasesBalances() throws Exception {
        MonetaryAmount withdrawal = MonetaryAmount.ars("30000.00");
        MonetaryAmount expectedTotal = INITIAL_CASH.subtract(withdrawal);

        OrderCreateRequestApi cashOutRequest = new TransferOrderRequestApi(
                savedUser.getId(),
                OrderSideApi.CASH_OUT,
                toApi(withdrawal));

        mockMvc.perform(post("/orders")
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cashOutRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"));

        PortfolioApi portfolio = fetchPortfolio(savedUser.getId());
        assertEquals(expectedTotal, toDomain(portfolio.availableCash()));
        assertEquals(expectedTotal, toDomain(portfolio.totalAccountValue()));
        assertTrue(portfolio.positions().isEmpty());
    }

    @Test
    @DisplayName("Scenario E: Sad Paths (Insufficient Balance CASH_OUT -> 409 Conflict, MARKET BUY -> REJECTED) leave portfolio 100% unchanged")
    void scenarioE_sadPathsLeavePortfolioUnchanged() throws Exception {
        OrderCreateRequestApi excessiveCashOut = new TransferOrderRequestApi(
                savedUser.getId(),
                OrderSideApi.CASH_OUT,
                toApi(MonetaryAmount.ars("150000.00")));

        mockMvc.perform(post("/orders")
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(excessiveCashOut)))
                .andExpect(status().isConflict());

        PortfolioApi afterFailedCashOut = fetchPortfolio(savedUser.getId());
        assertEquals(INITIAL_CASH, toDomain(afterFailedCashOut.availableCash()));
        assertEquals(INITIAL_CASH, toDomain(afterFailedCashOut.totalAccountValue()));
        assertTrue(afterFailedCashOut.positions().isEmpty());

        OrderCreateRequestApi excessiveBuy = new TradeOrderRequestApi(
                savedUser.getId(),
                pampInstrument.getId(),
                OrderSideApi.BUY,
                OrderTypeApi.MARKET,
                200,
                null,
                null);

        mockMvc.perform(post("/orders")
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(excessiveBuy)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        PortfolioApi afterRejectedBuy = fetchPortfolio(savedUser.getId());
        assertEquals(INITIAL_CASH, toDomain(afterRejectedBuy.availableCash()));
        assertEquals(INITIAL_CASH, toDomain(afterRejectedBuy.totalAccountValue()));
        assertTrue(afterRejectedBuy.positions().isEmpty());
    }

    private PortfolioApi fetchPortfolio(Long userId) throws Exception {
        MvcResult result = mockMvc.perform(get("/portfolios/{userId}", userId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PortfolioApi.class);
    }

    private static MonetaryAmount toDomain(com.cocos.trading.api.common.MonetaryAmount apiAmount) {
        return MonetaryAmount.of(apiAmount.amount(), apiAmount.currency());
    }

    private static com.cocos.trading.api.common.MonetaryAmount toApi(MonetaryAmount domainAmount) {
        return new com.cocos.trading.api.common.MonetaryAmount(domainAmount.amount(), domainAmount.currency());
    }
}

