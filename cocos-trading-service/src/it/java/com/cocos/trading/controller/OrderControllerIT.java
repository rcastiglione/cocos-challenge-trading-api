package com.cocos.trading.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocos.trading.TradingApplicationIT;
import com.cocos.trading.api.common.OrderSideApi;
import com.cocos.trading.api.common.OrderTypeApi;
import com.cocos.trading.api.request.OrderCreateRequestApi;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Functional integration tests for the {@code POST /orders} and {@code POST /orders/{orderId}/cancel} endpoints.
 */
@DisplayName("Order Controller Functional Integration Tests")
class OrderControllerIT extends TradingApplicationIT {

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

    private UserJpaEntity savedUser;
    private InstrumentJpaEntity arsInstrument;
    private InstrumentJpaEntity pampInstrument;

    @BeforeEach
    void setUpDatabase() {
        orderRepository.deleteAll();
        marketDataRepository.deleteAll();
        instrumentRepository.deleteAll();
        userRepository.deleteAll();

        savedUser = userRepository.save(new UserJpaEntity(null, "emiliano@test.com", "10001"));
        // Explicitly insert the mandatory 'MONEDA' (ARS) instrument required by TransferOrder / AccountLedger logic
        arsInstrument = instrumentRepository.save(
                new InstrumentJpaEntity(null, "ARS", "PESOS", InstrumentType.MONEDA)
        );
        pampInstrument = instrumentRepository.save(
                new InstrumentJpaEntity(null, "PAMP", "Pampa Energía S.A.", InstrumentType.ACCIONES)
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

        // Seed 50,000 ARS available cash via a FILLED CASH_IN order
        orderRepository.save(new OrderJpaEntity(
                null,
                arsInstrument.getId(),
                savedUser.getId(),
                50_000,
                new BigDecimal("1.00"),
                OrderType.MARKET,
                OrderSide.CASH_IN,
                OrderStatus.FILLED,
                LocalDateTime.of(2024, 7, 1, 10, 0)
        ));
    }

    @Test
    @DisplayName("Should execute MARKET BUY order immediately as FILLED when user has sufficient cash")
    void shouldExecuteMarketBuyOrderSuccessfully() throws Exception {
        OrderCreateRequestApi request = new OrderCreateRequestApi(
                savedUser.getId(),
                pampInstrument.getId(),
                OrderSideApi.BUY,
                OrderTypeApi.MARKET,
                10,
                null,
                null
        );

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(savedUser.getId()))
                .andExpect(jsonPath("$.instrumentId").value(pampInstrument.getId()))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.type").value("MARKET"))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.price").value(925.85))
                .andExpect(jsonPath("$.status").value("FILLED"));

        List<OrderJpaEntity> persistedOrders = orderRepository.findAll();
        assertEquals(2, persistedOrders.size());
    }

    @Test
    @DisplayName("Should execute MARKET BUY order by total investment amount in ARS (no fractional shares)")
    void shouldExecuteMarketBuyOrderByAmountSuccessfully() throws Exception {
        // 10,000 ARS / 925.85 = 10 whole shares
        OrderCreateRequestApi request = new OrderCreateRequestApi(
                savedUser.getId(),
                pampInstrument.getId(),
                OrderSideApi.BUY,
                OrderTypeApi.MARKET,
                null,
                new BigDecimal("10000.00"),
                null
        );

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.price").value(925.85))
                .andExpect(jsonPath("$.status").value("FILLED"));
    }

    @Test
    @DisplayName("Should reject and persist order as REJECTED when user has insufficient funds for BUY")
    void shouldRejectOrderDueToInsufficientFunds() throws Exception {
        // 100 shares * 925.85 = 92,585 ARS > 50,000 ARS available
        OrderCreateRequestApi request = new OrderCreateRequestApi(
                savedUser.getId(),
                pampInstrument.getId(),
                OrderSideApi.BUY,
                OrderTypeApi.MARKET,
                100,
                null,
                null
        );

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.price").value(925.85))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        List<OrderJpaEntity> rejectedOrders = orderRepository.findByUserIdAndStatusOrderByDatetimeAscIdAsc(
                savedUser.getId(),
                OrderStatus.REJECTED
        );
        assertEquals(1, rejectedOrders.size());
        assertEquals(100, rejectedOrders.getFirst().getSize());
    }

    @Test
    @DisplayName("Should reject and persist order as REJECTED when user has insufficient share holdings for SELL")
    void shouldRejectOrderDueToInsufficientHoldings() throws Exception {
        // User has 0 shares of PAMP and attempts to sell 5 shares
        OrderCreateRequestApi request = new OrderCreateRequestApi(
                savedUser.getId(),
                pampInstrument.getId(),
                OrderSideApi.SELL,
                OrderTypeApi.MARKET,
                5,
                null,
                null
        );

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("Should create LIMIT order with status NEW and allow cancelling it to CANCELLED")
    void shouldCreateLimitOrderAsNewAndCancelIt() throws Exception {
        OrderCreateRequestApi limitRequest = new OrderCreateRequestApi(
                savedUser.getId(),
                pampInstrument.getId(),
                OrderSideApi.BUY,
                OrderTypeApi.LIMIT,
                10,
                null,
                new BigDecimal("900.00")
        );

        String responseJson = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(limitRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.price").value(900.00))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long createdOrderId = objectMapper.readTree(responseJson).get("id").asLong();

        mockMvc.perform(post("/orders/{orderId}/cancel", createdOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdOrderId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when LIMIT order is missing price")
    void shouldReturnBadRequestWhenLimitOrderMissingPrice() throws Exception {
        OrderCreateRequestApi invalidLimitRequest = new OrderCreateRequestApi(
                savedUser.getId(),
                pampInstrument.getId(),
                OrderSideApi.BUY,
                OrderTypeApi.LIMIT,
                10,
                null,
                null
        );

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLimitRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("Should expose custom business metrics on /actuator/prometheus after processing orders")
    void shouldExposeCustomMetricsOnActuatorPrometheusEndpoint() throws Exception {
        OrderCreateRequestApi request = new OrderCreateRequestApi(
                savedUser.getId(),
                pampInstrument.getId(),
                OrderSideApi.BUY,
                OrderTypeApi.MARKET,
                5,
                null,
                null
        );

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"));

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cocos_orders_processed_total")))
                .andExpect(content().string(containsString("cocos_orders_execution_latency_seconds")));
    }

    @Test
    @DisplayName("Should execute CASH_OUT transfer when funds are sufficient and return BusinessRuleViolationProblem when insufficient")
    void shouldHandleCashOutTransferAndRejectExcessWithdrawal() throws Exception {
        // 1. Valid CASH_OUT of 20,000 ARS (out of 50,000 ARS available)
        OrderCreateRequestApi validCashOut = new OrderCreateRequestApi(
                savedUser.getId(),
                arsInstrument.getId(),
                OrderSideApi.CASH_OUT,
                OrderTypeApi.MARKET,
                20_000,
                null,
                null
        );

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCashOut)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.side").value("CASH_OUT"))
                .andExpect(jsonPath("$.size").value(20000))
                .andExpect(jsonPath("$.price").value(1.00))
                .andExpect(jsonPath("$.status").value("FILLED"));

        // 2. Excess CASH_OUT of 40,000 ARS (> 30,000 ARS remaining availableCash) -> 409 BusinessRuleViolationProblem
        OrderCreateRequestApi excessCashOut = new OrderCreateRequestApi(
                savedUser.getId(),
                arsInstrument.getId(),
                OrderSideApi.CASH_OUT,
                OrderTypeApi.MARKET,
                40_000,
                null,
                null
        );

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(excessCashOut)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business Rule Violation"));
    }
}

