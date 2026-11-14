package com.cocos.trading.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocos.trading.TradingApplicationIT;
import com.cocos.trading.api.OrderContractApi;
import com.cocos.trading.api.common.MonetaryAmount;
import com.cocos.trading.api.common.OrderSideApi;
import com.cocos.trading.api.common.OrderTypeApi;
import com.cocos.trading.api.request.OrderCreateRequestApi;
import com.cocos.trading.domain.model.InstrumentType;
import com.cocos.trading.domain.model.OrderSide;
import com.cocos.trading.domain.model.OrderStatus;
import com.cocos.trading.domain.model.OrderType;
import com.cocos.trading.repository.RedisIdempotencyAdapter;
import com.cocos.trading.repository.entity.InstrumentJpaEntity;
import com.cocos.trading.repository.entity.MarketDataJpaEntity;
import com.cocos.trading.repository.entity.OrderJpaEntity;
import com.cocos.trading.repository.entity.TradeOrderJpaEntity;
import com.cocos.trading.repository.entity.TransferOrderJpaEntity;
import com.cocos.trading.repository.entity.UserJpaEntity;
import com.cocos.trading.repository.jpa.SpringDataInstrumentRepository;
import com.cocos.trading.repository.jpa.SpringDataMarketDataRepository;
import com.cocos.trading.repository.jpa.SpringDataOrderRepository;
import com.cocos.trading.repository.jpa.SpringDataUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private UserJpaEntity savedUser;
    private InstrumentJpaEntity arsInstrument;
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
        orderRepository.save(new TransferOrderJpaEntity(
                null,
                arsInstrument,
                savedUser,
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
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.id").value(savedUser.getId()))
                .andExpect(jsonPath("$.user.accountNumber").value("10001"))
                .andExpect(jsonPath("$.instrument.id").value(pampInstrument.getId()))
                .andExpect(jsonPath("$.instrument.ticker").value("PAMP"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.type").value("MARKET"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.price.amount").value(925.85))
                .andExpect(jsonPath("$.price.currency").value("ARS"))
                .andExpect(jsonPath("$.status").value("FILLED"));

        List<OrderJpaEntity> persistedOrders = orderRepository.findByUserIdAndStatusOrderByDatetimeAscIdAsc(
                savedUser.getId(),
                OrderStatus.FILLED
        );
        assertEquals(2, persistedOrders.size());
        assertInstanceOf(TransferOrderJpaEntity.class, persistedOrders.get(0));
        assertInstanceOf(TradeOrderJpaEntity.class, persistedOrders.get(1));
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
                MonetaryAmount.ars(new BigDecimal("10000.00")),
                null
        );

        mockMvc.perform(post("/orders")
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.price.amount").value(925.85))
                .andExpect(jsonPath("$.price.currency").value("ARS"))
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
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(100))
                .andExpect(jsonPath("$.price.amount").value(925.85))
                .andExpect(jsonPath("$.price.currency").value("ARS"))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        List<OrderJpaEntity> rejectedOrders = orderRepository.findByUserIdAndStatusOrderByDatetimeAscIdAsc(
                savedUser.getId(),
                OrderStatus.REJECTED
        );
        assertEquals(1, rejectedOrders.size());
        assertEquals(100, rejectedOrders.getFirst().getQuantity());
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
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(5))
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
                MonetaryAmount.ars(new BigDecimal("900.00"))
        );

        String responseJson = mockMvc.perform(post("/orders")
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(limitRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.id").value(savedUser.getId()))
                .andExpect(jsonPath("$.user.accountNumber").value("10001"))
                .andExpect(jsonPath("$.instrument.id").value(pampInstrument.getId()))
                .andExpect(jsonPath("$.instrument.ticker").value("PAMP"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.price.amount").value(900.00))
                .andExpect(jsonPath("$.price.currency").value("ARS"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long createdOrderId = objectMapper.readTree(responseJson).get("id").asLong();

        mockMvc.perform(post("/orders/{orderId}/cancel", createdOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdOrderId))
                .andExpect(jsonPath("$.user.id").value(savedUser.getId()))
                .andExpect(jsonPath("$.user.accountNumber").value("10001"))
                .andExpect(jsonPath("$.instrument.id").value(pampInstrument.getId()))
                .andExpect(jsonPath("$.instrument.ticker").value("PAMP"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when LIMIT order is missing price or when currency is not ARS")
    void shouldReturnBadRequestWhenLimitOrderMissingPriceOrCurrencyInvalid() throws Exception {
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
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLimitRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));

        OrderCreateRequestApi unsupportedCurrencyRequest = new OrderCreateRequestApi(
                savedUser.getId(),
                pampInstrument.getId(),
                OrderSideApi.BUY,
                OrderTypeApi.LIMIT,
                10,
                null,
                new MonetaryAmount(new BigDecimal("900.00"), "USD")
        );

        mockMvc.perform(post("/orders")
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unsupportedCurrencyRequest)))
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
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
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
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCashOut)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.id").value(savedUser.getId()))
                .andExpect(jsonPath("$.user.accountNumber").value("10001"))
                .andExpect(jsonPath("$.instrument.id").value(arsInstrument.getId()))
                .andExpect(jsonPath("$.instrument.ticker").value("ARS"))
                .andExpect(jsonPath("$.side").value("CASH_OUT"))
                .andExpect(jsonPath("$.quantity").value(20000))
                .andExpect(jsonPath("$.price.amount").value(1.00))
                .andExpect(jsonPath("$.price.currency").value("ARS"))
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
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(excessCashOut)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business Rule Violation"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when Idempotency-Key header is missing or not a valid UUID")
    void shouldReturnBadRequestWhenIdempotencyKeyHeaderMissingOrInvalid() throws Exception {
        OrderCreateRequestApi request = new OrderCreateRequestApi(
                savedUser.getId(),
                pampInstrument.getId(),
                OrderSideApi.BUY,
                OrderTypeApi.MARKET,
                5,
                null,
                null
        );

        // Missing Idempotency-Key header
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));

        // Malformed UUID in Idempotency-Key header
        mockMvc.perform(post("/orders")
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, "invalid-uuid-format")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("Should store Idempotency-Key in Redis with 24h TTL and reject duplicate network retry with 409 Conflict")
    void shouldStoreIdempotencyKeyInRedisAndRejectDuplicateRetry() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        OrderCreateRequestApi cashInRequest = new OrderCreateRequestApi(
                savedUser.getId(),
                arsInstrument.getId(),
                OrderSideApi.CASH_IN,
                OrderTypeApi.MARKET,
                25_000,
                null,
                null
        );

        // 1st attempt succeeds (201 Created)
        mockMvc.perform(post("/orders")
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cashInRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FILLED"));

        // Verify key is stored in Redis with ~24h TTL (86,400s)
        String redisKey = RedisIdempotencyAdapter.buildRedisKey(idempotencyKey);
        assertEquals(Boolean.TRUE, stringRedisTemplate.hasKey(redisKey));
        Long ttlSeconds = stringRedisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        assertNotNull(ttlSeconds);
        assertTrue(ttlSeconds > 86_000L && ttlSeconds <= 86_400L, "Expected TTL close to 24h (86400s), got: " + ttlSeconds);

        // 2nd attempt (network retry with identical Idempotency-Key) is rejected with 409 Conflict
        mockMvc.perform(post("/orders")
                        .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cashInRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate Order"))
                .andExpect(jsonPath("$.parameters.idempotencyKey").value(idempotencyKey.toString()));

        // Ensure no duplicate order was persisted in PostgreSQL (1 initial seed + 1 new CASH_IN = 2 total)
        assertEquals(2, orderRepository.findAll().size());
    }

    @Test
    @DisplayName("Should prevent double-spending under concurrent duplicate requests sharing the same Idempotency-Key")
    void shouldPreventConcurrentDoubleSpendingWithSameIdempotencyKey() throws Exception {
        int concurrentRequests = 10;
        UUID sharedIdempotencyKey = UUID.randomUUID();
        OrderCreateRequestApi buyRequest = new OrderCreateRequestApi(
                savedUser.getId(),
                pampInstrument.getId(),
                OrderSideApi.BUY,
                OrderTypeApi.MARKET,
                10,
                null,
                null
        );
        String payload = objectMapper.writeValueAsString(buyRequest);

        CountDownLatch readyLatch = new CountDownLatch(concurrentRequests);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        AtomicInteger createdCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        try {
            List<Future<Integer>> futures = new ArrayList<>(concurrentRequests);
            for (int i = 0; i < concurrentRequests; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startGate.await(5, TimeUnit.SECONDS);
                    return mockMvc.perform(post("/orders")
                                    .header(OrderContractApi.IDEMPOTENCY_KEY_HEADER, sharedIdempotencyKey.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
            startGate.countDown();

            for (Future<Integer> future : futures) {
                int httpStatus = future.get(10, TimeUnit.SECONDS);
                if (httpStatus == 201) {
                    createdCount.incrementAndGet();
                } else if (httpStatus == 409) {
                    conflictCount.incrementAndGet();
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, createdCount.get(), "Exactly one request must succeed with 201 Created");
        assertEquals(concurrentRequests - 1, conflictCount.get(), "All duplicate concurrent requests must fail with 409 Conflict");
        // 1 initial seed order + 1 executed BUY order = 2 total orders in DB
        assertEquals(2, orderRepository.findAll().size());
    }
}
