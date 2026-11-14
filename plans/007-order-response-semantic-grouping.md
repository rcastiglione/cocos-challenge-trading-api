# Spec: Herencia Polimórfica en JPA (`SINGLE_TABLE` + `@DiscriminatorFormula`), Arquitectura Pragmática Data-Centric, Simetría Semántica y Unificación de Vocabulario (`quantity`) en `OrderApi`
**Status:** Completed (v6 — Unificación de Vocabulario `size` $\rightarrow$ `quantity` + Herencia JPA `SINGLE_TABLE` + Modelo Anémico)
**Context:** Cocos Challenge Trading API
**Architecture:** Multi-Module Pragmatic Service-Driven Architecture (Java 21, Spring Boot 3.3, Hibernate 6 `@DiscriminatorFormula`, Framework-Agnostic API Contract)

## Objetivo
Consolidar una **Arquitectura Pragmática Data-Centric (Anemic Domain Model)** y garantizar **consistencia semántica y de vocabulario extremo a extremo** entre todos los endpoints de la API (`GET /portfolios/{userId}`, `POST /orders`, `POST /orders/{orderId}/cancel`) y el modelo de persistencia JPA:

1. **Unificación de Vocabulario (`size` $\rightarrow$ `quantity`) en Contrato, Aplicación y JPA**:
   - Actualmente, `GET /portfolios/{userId}` expone la tenencia de acciones en cada posición bajo la propiedad `quantity` (`AssetPositionApi.quantity`), mientras que `POST /orders` utiliza `size` tanto en el request (`OrderCreateRequestApi`) como en la respuesta (`OrderApi`).
   - Reemplazar `size` por `quantity` a lo largo de toda la API (`OrderCreateRequestApi`, `OrderApi`, `OrderCreateRequestValidator`), capa de aplicación (`PlaceOrderCommand`, `AccountLedgerCalculator`, `PortfolioService`, `OrderService`, estrategias de ejecución) y capa de persistencia (`OrderJpaEntity` y sus subclases `TradeOrderJpaEntity` y `TransferOrderJpaEntity`).
   - En `OrderJpaEntity`, mapear el atributo `private Integer quantity;` contra la columna física existente mediante `@Column(name = "size", nullable = false)` para preservar **Zero DDL Schema Mutation**.
2. **Herencia Polimórfica en JPA (`OrderJpaEntity` $\rightarrow$ `TradeOrderJpaEntity` | `TransferOrderJpaEntity`)**:
   - Mantener `OrderJpaEntity` como clase `abstract` anotada con `@Inheritance(strategy = InheritanceType.SINGLE_TABLE)` y `@DiscriminatorFormula("CASE WHEN side IN ('CASH_IN', 'CASH_OUT') THEN 'TRANSFER' ELSE 'TRADE' END")`.
   - Mantener las dos subclases concretas anémicas:
     - `TradeOrderJpaEntity` (`@Entity`, `@DiscriminatorValue("TRADE")`): operaciones de mercado (`BUY`, `SELL`) sobre activos de renta variable (`ACCIONES`).
     - `TransferOrderJpaEntity` (`@Entity`, `@DiscriminatorValue("TRANSFER")`): movimientos de efectivo (`CASH_IN`, `CASH_OUT`) sobre instrumentos monetarios (`MONEDA`).
3. **Impacto Cero en Base de Datos (Zero DDL Mutation)**:
   - Tanto `@DiscriminatorFormula` sobre `side`, como `@ManyToOne(fetch = FetchType.LAZY)` con `@JoinColumn(name = "userid" / "instrumentid")`, y `@Column(name = "size", nullable = false)` sobre el campo `quantity` operan exclusivamente en los metadatos de Hibernate. La tabla física `orders` en PostgreSQL/NeonDB (`id, instrumentid, userid, size, price, type, side, status, datetime`) permanece **100% inalterada**.
4. **Modelo Anémico Unificado y Eliminación de Mappers Redundantes**:
   - Mantener eliminadas las clases espejo (`Order`, `TradeOrder`, `TransferOrder`, `User`, `Instrument`, `MarketData`) y `PersistenceMapper`.
   - Toda la lógica de negocio vive en los **Servicios, Estrategias y Calculadoras** (`AccountLedgerCalculator`, `OrderService`, `PortfolioService`, `InstrumentService`, `OrderExecutionStrategy`).
5. **Simetría Semántica en el Contrato Público (`OrderApi` y `OrderCreateRequestApi`)**:
   - Mantener los objetos anidados `UserSummaryApi user` (`{ "id", "accountNumber" }`), `InstrumentSummaryApi instrument` (`{ "id", "ticker" }`), `MonetaryAmount price` y la propiedad unificada `Integer quantity`.

---

## Estructura Objetivo Consolidada del Contrato JSON (`POST /orders` y `POST /orders/{orderId}/cancel`)

### Ejemplo de Request (`POST /orders` por cantidad exacta `quantity`)
```json
{
  "userId": 1,
  "instrumentId": 47,
  "side": "BUY",
  "type": "MARKET",
  "quantity": 10
}
```

### Ejemplo de Response (`POST /orders` y `POST /orders/{orderId}/cancel`)
```json
{
  "id": 105,
  "user": {
    "id": 1,
    "accountNumber": "10001"
  },
  "instrument": {
    "id": 47,
    "ticker": "PAMP"
  },
  "quantity": 10,
  "price": {
    "amount": 925.85,
    "currency": "ARS"
  },
  "type": "MARKET",
  "side": "BUY",
  "status": "FILLED",
  "datetime": "2024-06-15T14:30:00"
}
```

---

## Pasos de Implementación y Trazabilidad SDD

### 1. Actualización del Contrato API (`cocos-trading-contract`) y Validador Web
Unificar el nombre del campo `quantity` en los DTOs de entrada y salida de órdenes:

*   **[OrderCreateRequestApi.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-contract/src/main/java/com/cocos/trading/api/request/OrderCreateRequestApi.java):**
    *   Reemplazar el componente `Integer size` del `record` por:
        ```java
        @Schema(description = "Exact quantity of shares (or ARS units for MONEDA) to trade", example = "10")
        Integer quantity,
        ```
    *   Actualizar el Javadoc del record para referenciar `quantity` en lugar de `size`.
*   **[OrderApi.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-contract/src/main/java/com/cocos/trading/api/response/OrderApi.java):**
    *   Reemplazar el componente `Integer size` del `record` por:
        ```java
        @Schema(description = "Order quantity (number of shares or cash units)", example = "10")
        Integer quantity,
        ```
    *   Conservar los objetos semánticos `UserSummaryApi user`, `InstrumentSummaryApi instrument` y `MonetaryAmount price`.
*   **[OrderCreateRequestValidator.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/controller/validator/OrderCreateRequestValidator.java):**
    *   Actualizar la validación del campo `size` a `quantity` (`request.quantity()`), incluyendo el nombre del campo rechazado (`"quantity"`), los códigos (`"quantityOrAmount.required"`, `"quantityOrAmount.ambiguous"`, `"quantity.invalid"`) y los mensajes descriptivos (`"Either quantity (exact shares) or amount (total investment in ARS) must be provided."`, `"Provide either quantity or amount, not both."`, `"quantity must be greater than 0."`).

---

### 2. Herencia `SINGLE_TABLE` en JPA y Renombrado de `size` $\rightarrow$ `quantity` con `@Column(name = "size")`
Actualizar la jerarquía JPA en `com.cocos.trading.repository.entity` manteniendo **Zero Schema Mutation**:

*   **Clase Base Abstracta [OrderJpaEntity.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/repository/entity/OrderJpaEntity.java):**
    *   Mantener `@Inheritance(strategy = InheritanceType.SINGLE_TABLE)`, `@DiscriminatorFormula("CASE WHEN side IN ('CASH_IN', 'CASH_OUT') THEN 'TRANSFER' ELSE 'TRADE' END")`, `@SuperBuilder(toBuilder = true)` y las relaciones `@ManyToOne(fetch = FetchType.LAZY, optional = false)` hacia `UserJpaEntity user` (`userid`) e `InstrumentJpaEntity instrument` (`instrumentid`).
    *   Renombrar el atributo `size` a `quantity`, manteniendo el mapeo explícito hacia la columna física `size` de la tabla `orders`:
        ```java
        @Column(name = "size", nullable = false)
        private Integer quantity;
        ```
    *   Con esto, LomboK (`@Getter`, `@Setter`, `@SuperBuilder`) genera `getQuantity()`, `setQuantity(Integer)` y `.quantity(...)` en toda la jerarquía (`OrderJpaEntity`, `TradeOrderJpaEntity`, `TransferOrderJpaEntity`), mientras Hibernate sigue leyendo y escribiendo en la columna SQL `orders.size`.
*   **Subclases Concretas [TradeOrderJpaEntity.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/repository/entity/TradeOrderJpaEntity.java) y [TransferOrderJpaEntity.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/repository/entity/TransferOrderJpaEntity.java):**
    *   Heredan automáticamente la propiedad `quantity` a través de `@SuperBuilder(toBuilder = true)` y `OrderJpaEntity`.
*   **Repositorio [SpringDataOrderRepository.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/repository/jpa/SpringDataOrderRepository.java):**
    *   Conserva `@EntityGraph(attributePaths = {"user", "instrument"})` en las consultas de órdenes.

---

### 3. Actualización de Comandos, Servicios, Estrategias y `ApiMapper`
Propagar el vocabulario `quantity` en la capa de aplicación y mapeo:

*   **[PlaceOrderCommand.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/port/in/model/PlaceOrderCommand.java):**
    *   Renombrar `Integer size` a `Integer quantity`.
*   **[ApiMapper.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/controller/mapper/ApiMapper.java):**
    *   En `toCommand(UUID idempotencyKey, OrderCreateRequestApi request)`: mapear `.quantity(request.quantity())`.
    *   En `toApi(OrderJpaEntity order)`: mapear `.quantity(order.getQuantity())` junto con `.user(toUserSummaryApi(order.getUser()))` e `.instrument(toInstrumentSummaryApi(order.getInstrument()))`.
*   **[AccountLedgerCalculator.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/service/AccountLedgerCalculator.java):**
    *   Usar `order.getQuantity()` y `tradeOrder.getQuantity()` en `getTotalAmount(OrderJpaEntity)`, `calculateCashImpact(OrderJpaEntity)` y `calculateAvailableShares(...)`.
    *   Renombrar `calculateMaxShareSize(...)` a `calculateMaxShareQuantity(MonetaryAmount investmentAmount, BigDecimal executionPrice)`.
*   **Estrategias de Ejecución ([AbstractEquityOrderExecutionStrategy.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/strategy/AbstractEquityOrderExecutionStrategy.java) y [CashTransferExecutionStrategy.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/strategy/CashTransferExecutionStrategy.java)):**
    *   En `AbstractEquityOrderExecutionStrategy`: resolver `command.quantity()` o `AccountLedgerCalculator.calculateMaxShareQuantity(command.amount(), executionPrice)` y construir `TradeOrderJpaEntity.builder()...quantity(resolvedQuantity)...build()`.
    *   En `CashTransferExecutionStrategy`: resolver `command.quantity()` o `command.amount()` y construir `TransferOrderJpaEntity.builder()...quantity(transferQuantity)...build()`.
*   **[PortfolioService.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/service/PortfolioService.java) y [OrderService.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/service/OrderService.java):**
    *   En `PortfolioService.PositionAccumulator`: usar `order.getQuantity()` directamente al acumular las posiciones (`quantity`).
    *   En `OrderService`: actualizar el log de orden procesada para registrar `quantity={}` (`savedOrder.getQuantity()`).

---

### 4. Actualización de Colección de Postman y Documentación (`README.md`)
*   **[cocos-trading-api.postman_collection.json](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-api.postman_collection.json):**
    *   En todos los bodies de request de `POST /orders` por cantidad de acciones o unidades de moneda (`BUY (by exact share quantity)`, `CASH_IN`, `CASH_OUT`), reemplazar `"size"` por `"quantity"`.
    *   En todos los ejemplos de respuesta (`response` array de `POST /orders` para `MARKET`, `LIMIT`, `CASH_IN`, `CASH_OUT` y `POST /orders/{orderId}/cancel`), reemplazar `"size"` por `"quantity"`, manteniendo los objetos anidados `user`, `instrument` y `price`.
*   **[README.md](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/README.md):**
    *   Actualizar los ejemplos `cURL` y bloques JSON de `POST /orders` y `POST /orders/{orderId}/cancel` para usar `"quantity"` en lugar de `"size"`.
    *   En la sección **Design Decisions (`3.2` / `3.6`)**, documentar la unificación del vocabulario (`quantity`) entre `GET /portfolios/{userId}` y `POST /orders`, así como el mapeo `@Column(name = "size")` en `OrderJpaEntity` para mantener compatibilidad total sin alterar el esquema SQL.

---

### 5. Actualización de Tests Unitarios y de Integración
*   Actualizar [TradingFixtures.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/test/java/com/cocos/trading/fixture/TradingFixtures.java), [DomainModelTest.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/test/java/com/cocos/trading/domain/model/DomainModelTest.java), [OrderServiceTest.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/test/java/com/cocos/trading/application/service/OrderServiceTest.java), [OrderControllerIT.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/it/java/com/cocos/trading/controller/OrderControllerIT.java) y [PortfolioAndInstrumentControllerIT.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/it/java/com/cocos/trading/controller/PortfolioAndInstrumentControllerIT.java) para utilizar `.quantity(...)`, `getQuantity()` y los JSON paths `$.quantity` tanto en los payloads enviados como en las aserciones de respuesta.
*   Ejecutar `mvn clean verify` hasta confirmar que el 100% de los tests unitarios y de integración con Testcontainers pasen en verde.
