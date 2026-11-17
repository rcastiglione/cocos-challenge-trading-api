# Plan 011: Diagnóstico de Bug en Portfolio Lifecycle, Typed Subtype Queries & Integration Test Suite (`PortfolioLifecycleIT`)

**Status:** Completed (Build 100% Green, All Tests Verified)
**Context:** Cocos Challenge Trading API
**Architecture:** Multi-Module Pragmatic Service-Driven Architecture (Java 21, Spring Boot 3.3, PostgreSQL 16 + Flyway / `init.sql`, Redis 7, Hibernate `SINGLE_TABLE` Polymorphic Orders)

---

## 1. Gentle AI & Engram ReAct Loop — Diagnóstico del Bug & Fix Strategy

### Thought (Análisis de Root Cause y Descarte de `Hibernate.unproxy()`)
Al ejecutar la aplicación localmente vía Docker, se reportó que tras un `POST /orders` (`MARKET BUY`) que retorna `HTTP 201`, el endpoint `GET /portfolios/{userId}` no refleja los cambios en `availableCash` ni en el `quantity` del instrumento.

Al analizar `OrderController`, `OrderService`, `AbstractEquityOrderExecutionStrategy`, `OrderRepositoryAdapter`, `SpringDataOrderRepository`, `OrderJpaEntity`, `AccountLedgerCalculator` y `PortfolioService`, se identifican los puntos críticos:

1. **Ambigüedad Polimórfica en Queries Genéricas (`OrderJpaEntity` vs. Subtipos Concretos)**:
   - `OrderJpaEntity` usa `@Inheritance(strategy = InheritanceType.SINGLE_TABLE)` con `@DiscriminatorFormula("CASE WHEN side IN ('CASH_IN', 'CASH_OUT') THEN 'TRANSFER' ELSE 'TRADE' END")`.
   - Tanto `PortfolioService.getPortfolio` (`if (!(order instanceof TradeOrderJpaEntity tradeOrder))`) como `AccountLedgerCalculator` (`switch (order) { case TradeOrderJpaEntity ... case TransferOrderJpaEntity ... }`) dependen de recibir instancias concretas (`TradeOrderJpaEntity` o `TransferOrderJpaEntity`).
   - Consultar siempre la superclase abstracta `OrderJpaEntity` expone a ambigüedades de proxies polimórficos de Hibernate (`HibernateProxy`).
   - **Por qué descartamos `Hibernate.unproxy()`**: Utilizar `Hibernate.unproxy()` acopla la capa de aplicación/persistencia al ciclo de vida interno de proxies de Hibernate, es un *workaround* frágil y puede provocar `LazyInitializationException` inesperadas fuera de sesión (`open-in-view: false`).
   - **Solución JPA Estándar**: Consultar directamente los subtipos concretos mediante **JPQL Queries tipadas** en `SpringDataOrderRepository`. Al consultar `FROM TradeOrderJpaEntity t ...` y `FROM TransferOrderJpaEntity tr ...`, el proveedor JPA instancia directamente las clases concretas con `@EntityGraph(attributePaths = {"user", "instrument"})`, eliminando por diseño cualquier ambigüedad de proxy.

2. **Orden `REJECTED` Silenciosa Retornando `HTTP 201` (`OrderController` + `AbstractEquityOrderExecutionStrategy`)**:
   - `OrderController.create` retorna `HTTP 201 Created` incluso cuando `AbstractEquityOrderExecutionStrategy.determineStatus` persiste la orden con `OrderStatus.REJECTED` (saldo insuficiente o `amount < executionPrice`), mientras que `PortfolioService` agrega únicamente órdenes `OrderStatus.FILLED`.

3. **Idempotent Replay por Reutilización de `Idempotency-Key` (`OrderService.placeOrder` Step B)**:
   - Reenviar el mismo `Idempotency-Key` con idéntico payload retorna `HTTP 201` con la orden existente sin ejecutar una nueva mutación en DB.

### Action (Estrategia de Fix Basada en Typed JPQL Queries)
1. **Remover cualquier uso de `Hibernate.unproxy()`** en `OrderRepositoryAdapter` (mantener la capa de persistencia 100% agnóstica de APIs internas de Hibernate).
2. **Definir Typed Subtype Queries en `SpringDataOrderRepository`**:
   - `findTradeOrdersByUserIdAndStatus(Long userId, OrderStatus status)` con `@Query("SELECT t FROM TradeOrderJpaEntity t WHERE t.user.id = :userId AND t.status = :status ORDER BY t.datetime ASC, t.id ASC")` y `@EntityGraph(attributePaths = {"user", "instrument"})`, retornando `List<TradeOrderJpaEntity>`.
   - `findTransferOrdersByUserIdAndStatus(Long userId, OrderStatus status)` con `@Query("SELECT tr FROM TransferOrderJpaEntity tr WHERE tr.user.id = :userId AND tr.status = :status ORDER BY tr.datetime ASC, tr.id ASC")` y `@EntityGraph(attributePaths = {"user", "instrument"})`, retornando `List<TransferOrderJpaEntity>`.
3. **Exponer Métodos Tipados en `OrderRepositoryPort` y `OrderRepositoryAdapter`**:
   - Agregar `List<TradeOrderJpaEntity> findTradeOrdersByUserIdAndStatus(Long userId, OrderStatus status)` y `List<TransferOrderJpaEntity> findTransferOrdersByUserIdAndStatus(Long userId, OrderStatus status)`.
4. **Consumir Instancias Limpias en `PortfolioService` y `AccountLedgerCalculator` / `OrderService`**:
   - `PortfolioService.getPortfolio` y `AccountLedgerCalculator` operan directamente sobre listas tipadas (`List<TradeOrderJpaEntity>` y `List<TransferOrderJpaEntity>`), evitando casts frágiles y garantizando aritmética exacta sobre `MonetaryAmount`.

### Observation (Garantías Esperadas)
- Cero acoplamiento a `org.hibernate.Hibernate` en servicios y adaptadores.
- Cero riesgo de `HibernateProxy` o `LazyInitializationException` al procesar `TradeOrderJpaEntity` y `TransferOrderJpaEntity` con `open-in-view: false`.

---

## 2. Diseño del Robust E2E Test Suite (`PortfolioLifecycleIT`)

### Ubicación & Stack
- **Archivo:** `cocos-trading-service/src/it/java/com/cocos/trading/controller/PortfolioLifecycleIT.java`
- **Base Class:** `TradingApplicationIT` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@AutoConfigureMockMvc`, Testcontainers `postgres:16-alpine` + `redis:7-alpine`).
- **Validación:** Todas las aserciones monetarias usan el Value Object `MonetaryAmount` (`amount` con scale 2 + `currency = "ARS"`).

### Fixture Base (`@BeforeEach`)
- Limpieza completa de Redis (`flushAll()`) y PostgreSQL (`orders`, `marketdata`, `instruments`, `users`).
- Seed de `UserJpaEntity` (`emiliano@test.com`), `ARS` (`MONEDA`), `PAMP` (`ACCIONES`), `MarketDataJpaEntity` (`close = 1000.00`, `previousClose = 950.00`) y depósito inicial `CASH_IN` `FILLED` de `100,000.00 ARS`.

### Escenario Core Destacado: Check Portfolio -> Market Buy -> Check Portfolio Again
1. **Check Portfolio (Before):** `GET /portfolios/{userId}` -> verificar `200 OK`, `availableCash == 100,000.00 ARS`, `totalAccountValue == 100,000.00 ARS`, `positions` vacío.
2. **Execute Market Buy:** `POST /orders` (`BUY`, `MARKET`, `PAMP`, `quantity = 10`) con `Idempotency-Key` único -> verificar `201 Created`, `$.status == "FILLED"`, `$.price.amount == 1000.00`.
3. **Check Portfolio Again (After):** `GET /portfolios/{userId}` -> verificar `200 OK`:
   - `availableCash` disminuyó exactamente en `(10 * 1000.00) = 10,000.00 ARS` -> `90,000.00 ARS`.
   - `positions` tiene 1 posición (`PAMP`, `quantity = 10`, `marketValue = 10,000.00 ARS`).
   - `totalAccountValue` se mantiene matemáticamente consistente en `100,000.00 ARS` (`90,000.00 + 10,000.00`).

### Escenarios Adicionales Cubiertos
- **Scenario B — Market Sell (Happy Path):** Ejecutar `BUY` (`10` acciones) seguido de `SELL` (`4` acciones) -> verificar `positions[0].quantity == 6`, `availableCash == 94,000.00 ARS`, `totalAccountValue == 100,000.00 ARS`.
- **Scenario C — Cash-In / Deposit (Happy Path):** `POST /orders` (`CASH_IN`, `25,000.00 ARS`) -> verificar que `availableCash` y `totalAccountValue` suben a `125,000.00 ARS`.
- **Scenario D — Cash-Out / Withdrawal (Happy Path):** `POST /orders` (`CASH_OUT`, `30,000.00 ARS`) -> verificar que `availableCash` y `totalAccountValue` bajan a `70,000.00 ARS`.
- **Scenario E — Sad Paths (Insufficient Balances):**
  - `CASH_OUT` excediendo saldo (`150,000.00 ARS`) -> `409 Conflict`; `GET /portfolios/{userId}` confirma balances 100% intactos.
  - `MARKET BUY` excediendo saldo (`200` acciones = `200,000.00 ARS`) -> `201 Created` con `$.status == "REJECTED"`; `GET /portfolios/{userId}` confirma `availableCash`, `totalAccountValue` y `positions` 100% intactos.

---

## 3. Plan de Ejecución y Verificación (Post-Aprobación)
1. Remover `Hibernate.unproxy()` de `OrderRepositoryAdapter`.
2. Implementar `findTradeOrdersByUserIdAndStatus` y `findTransferOrdersByUserIdAndStatus` en `SpringDataOrderRepository`, `OrderRepositoryPort` y `OrderRepositoryAdapter`, y actualizar `PortfolioService` / `AccountLedgerCalculator` / `OrderService` para consumir los métodos tipados.
3. Actualizar `PortfolioLifecycleIT` para incluir el flujo explícito **Check Portfolio -> Market Buy -> Check Portfolio Again** junto con todos los escenarios A–E.
4. Ejecutar `mvn clean verify` y validar build 100% green.
