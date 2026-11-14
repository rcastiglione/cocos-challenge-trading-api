# Spec: Implementación del Money Pattern (`MonetaryAmount`) y Agrupamiento Semántico de Entidades (`user` & `instrument`)
**Status:** Completed
**Context:** Cocos Challenge Trading API
**Architecture:** Multi-Module Hexagonal / Tactical DDD (Java 21, Spring Boot 3.3, Framework-Agnostic API Contract)

## Objetivo
Refinar la arquitectura del motor de *trading* aplicando diseño táctico de **DDD (Domain-Driven Design)** y estándares de **APIs Fintech modernas** tanto en el **Contrato Público (`cocos-trading-contract`)** como en el **Modelo de Dominio (`cocos-trading-service`)**:

1. **Money Pattern (`MonetaryAmount` en Contrato y Dominio)**: Erradicar el *anti-pattern* de exponer montos financieros como números escalares planos (`BigDecimal`) en el JSON y eliminar la *Primitive Obsession* en el dominio encapsulando valor, moneda (`"ARS"`), escala (`2`, `RoundingMode.HALF_UP`) y aritmética segura (*currency-safe arithmetic*) dentro de un *Value Object* inmutable.
2. **Agrupamiento Semántico de Entidades (`user` e `instrument`) en el Contrato y Dominio**:
   - **En la raíz de `Portfolio` (`GET /portfolios/{userId}`)**: Reemplazar los campos sueltos `userId` y `accountNumber` por un sub-objeto cohesivo `"user": { "id", "accountNumber" }` (`UserSummaryApi` en el contrato y la entidad `User` en el modelo de dominio `Portfolio`).
   - **En cada elemento de `positions`**: Reemplazar los campos planos del activo por un sub-objeto compacto `"instrument": { "id", "ticker" }` (`InstrumentSummaryApi` en el contrato y la entidad `Instrument` en el modelo de dominio `AssetPosition`), manteniendo el *payload* liviano y separando la identidad del instrumento respecto de las métricas dinámicas de valuación para mejorar la **Developer Experience (DX)**.
3. **Zero Schema Mutation vía Anti-Corruption Layer (`PersistenceMapper`)**: Mantener 100% intacto el esquema relacional de PostgreSQL/NeonDB provisto por el evaluador delegando en `PersistenceMapper` la hidratación y desencapsulación de `MonetaryAmount`.

### Estructura Objetivo Consolidada del JSON de Respuesta (`GET /portfolios/1`)
```json
{
  "user": {
    "id": 1,
    "accountNumber": "10001"
  },
  "totalAccountValue": { "amount": 1697459.88, "currency": "ARS" },
  "availableCash": { "amount": 1286738.70, "currency": "ARS" },
  "positions": [
    {
      "instrument": {
        "id": 47,
        "ticker": "PAMP"
      },
      "quantity": 298,
      "lastPrice": { "amount": 925.85, "currency": "ARS" },
      "marketValue": { "amount": 275903.30, "currency": "ARS" },
      "totalReturnPercentage": 2.15,
      "dailyReturnPercentage": 1.55
    }
  ]
}
```

---

## Pasos de Implementación y Trazabilidad SDD

### 1. Actualización del Contrato de la API (`cocos-trading-contract`)
Actualizar el módulo `cocos-trading-contract` manteniendo su aislamiento *framework-agnostic* (`lombok`, `jackson-annotations`, `swagger-annotations-jakarta`):

*   **Nuevo DTO `MonetaryAmount` (`cocos-trading-contract/src/main/java/com/cocos/trading/api/common/MonetaryAmount.java`):**
    *   Definir `@Builder public record MonetaryAmount(BigDecimal amount, String currency)` anotado con `@Schema` de OpenAPI (`amount` ej. `"925.85"`, `currency` ISO 4217 ej. `"ARS"`).
    *   Incluir constante `DEFAULT_CURRENCY = "ARS"` y *factory method* `MonetaryAmount.ars(BigDecimal amount)`.
*   **Nuevos Sub-Objetos de Agrupamiento Semántico (`cocos-trading-contract/src/main/java/com/cocos/trading/api/response/`):**
    *   `UserSummaryApi.java`: Definir `@Builder public record UserSummaryApi(Long id, String accountNumber)` para encapsular la identidad de la cuenta comitente del usuario bajo la propiedad `"user"`.
    *   `InstrumentSummaryApi.java`: Definir `@Builder public record InstrumentSummaryApi(Long id, String ticker)` para encapsular la identidad compacta del activo bajo la propiedad `"instrument"` (omitiendo `name` para evitar sobrecargar innecesariamente el tamaño del *payload* en el *array* de posiciones).
*   **Actualización de Response DTOs (`cocos-trading-contract`):**
    *   [PortfolioApi.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-contract/src/main/java/com/cocos/trading/api/response/PortfolioApi.java):
        *   Reemplazar `Long userId` y `String accountNumber` por el sub-objeto anidado `UserSummaryApi user`.
        *   `BigDecimal totalAccountValue` $\rightarrow$ `MonetaryAmount totalAccountValue`
        *   `BigDecimal availableCash` $\rightarrow$ `MonetaryAmount availableCash`
    *   [AssetPositionApi.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-contract/src/main/java/com/cocos/trading/api/response/AssetPositionApi.java):
        *   Reemplazar los campos planos `Long instrumentId`, `String ticker`, `String name` por el sub-objeto anidado `InstrumentSummaryApi instrument` (`{ "id", "ticker" }`).
        *   `BigDecimal lastPrice` $\rightarrow$ `MonetaryAmount lastPrice`
        *   `BigDecimal marketValue` $\rightarrow$ `MonetaryAmount marketValue`
        *   *(Nota: `quantity` permanece como `Integer`, y `totalReturnPercentage` / `dailyReturnPercentage` permanecen como `BigDecimal` por ser variaciones porcentuales `%`).*
    *   [InstrumentApi.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-contract/src/main/java/com/cocos/trading/api/response/InstrumentApi.java):
        *   `BigDecimal lastPrice` $\rightarrow$ `MonetaryAmount lastPrice`
        *   `BigDecimal previousClose` $\rightarrow$ `MonetaryAmount previousClose`
    *   [OrderApi.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-contract/src/main/java/com/cocos/trading/api/response/OrderApi.java):
        *   `BigDecimal price` $\rightarrow$ `MonetaryAmount price`
*   **Actualización de Request DTOs (`cocos-trading-contract`):**
    *   [OrderCreateRequestApi.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-contract/src/main/java/com/cocos/trading/api/request/OrderCreateRequestApi.java):
        *   `BigDecimal amount` $\rightarrow$ `MonetaryAmount amount`
        *   `BigDecimal price` $\rightarrow$ `MonetaryAmount price`

---

### 2. Value Object de Dominio (`MonetaryAmount`) y Composición Rica en `Portfolio` y `AssetPosition`
Enriquecer el modelo de dominio en `com.cocos.trading.domain.model` (`cocos-trading-service`) eliminando la *Primitive Obsession* y componiendo las entidades de dominio:

*   **Nuevo Domain Value Object `MonetaryAmount` (`cocos-trading-service/src/main/java/com/cocos/trading/domain/model/MonetaryAmount.java`):**
    *   `public record MonetaryAmount(BigDecimal amount, String currency)`:
        *   **Invariantes en Constructor Compacto**: Valida que `amount != null` y `currency != null && !currency.isBlank()`, normalizando la divisa (`currency.trim().toUpperCase()`) y la escala (`CURRENCY_SCALE = 2`, `RoundingMode.HALF_UP`).
        *   **Constantes y Factories**: `ARS_CURRENCY = "ARS"`, `ZERO_ARS = MonetaryAmount.ars(BigDecimal.ZERO)`, `ONE_ARS = MonetaryAmount.ars(BigDecimal.ONE)`, `MonetaryAmount.ars(BigDecimal amount)`, `MonetaryAmount.of(BigDecimal amount, String currency)`.
        *   **Operaciones Seguras (*Currency-Safe Arithmetic*)**:
            *   `add(MonetaryAmount other)` y `subtract(MonetaryAmount other)` con chequeo `assertSameCurrency(other)` (lanza `BusinessRuleViolationException` ante intento de operar monedas distintas).
            *   `multiply(int quantity)` / `multiply(BigDecimal factor)`, `negate()`, y `divideToIntegralShares(MonetaryAmount unitPrice)`.
            *   Predicados: `isPositive()`, `isZeroOrNegative()`, `isLessThan(MonetaryAmount other)`, `isGreaterThan(MonetaryAmount other)`.
*   **Composición Rica en `Portfolio` y `AssetPosition` (`cocos-trading-service`):**
    *   [Portfolio.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/domain/model/Portfolio.java):
        *   Reemplazar `Long userId` y `String accountNumber` por la entidad de dominio `User user`.
        *   Usar `MonetaryAmount` para `totalAccountValue` y `availableCash`.
    *   [AssetPosition.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/domain/model/AssetPosition.java):
        *   Reemplazar `Long instrumentId`, `String ticker`, `String name` por la entidad de dominio `Instrument instrument`.
        *   Usar `MonetaryAmount` para `lastPrice` y `marketValue`.
    *   [Order.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/domain/model/Order.java), [TradeOrder.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/domain/model/TradeOrder.java) y [TransferOrder.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/domain/model/TransferOrder.java):
        *   Cambiar `BigDecimal price` $\rightarrow$ `MonetaryAmount price` (`UNIT_CASH_PRICE = MonetaryAmount.ONE_ARS`), `getTotalAmount()` $\rightarrow$ `MonetaryAmount` y `getCashImpact()` $\rightarrow$ `MonetaryAmount`.
    *   [MarketData.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/domain/model/MarketData.java) e [InstrumentQuote.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/domain/model/InstrumentQuote.java):
        *   Usar `MonetaryAmount` para `high`, `low`, `open`, `close`, `previousClose`, `lastPrice`.
    *   **Servicios y Estrategias** ([PortfolioService.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/service/PortfolioService.java), [AccountLedgerCalculator.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/service/AccountLedgerCalculator.java), [InstrumentService.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/service/InstrumentService.java), [PlaceOrderCommand.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/port/in/model/PlaceOrderCommand.java), [OrderExecutionContext.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/strategy/OrderExecutionContext.java), [AbstractEquityOrderExecutionStrategy.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/strategy/AbstractEquityOrderExecutionStrategy.java), [MarketOrderExecutionStrategy.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/strategy/MarketOrderExecutionStrategy.java), [LimitOrderExecutionStrategy.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/strategy/LimitOrderExecutionStrategy.java), [CashTransferExecutionStrategy.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/strategy/CashTransferExecutionStrategy.java)):
        *   Operar nativamente con `MonetaryAmount`, construir `Portfolio` pasando `user` y `AssetPosition` pasando `instrument`, y ordenar posiciones por `pos.instrument().ticker()`.

---

### 3. Capas de Mapeo (`PersistenceMapper` como ACL + `ApiMapper` y Validación Web)
Conectar la persistencia y la capa HTTP sin mutar el esquema de base de datos:

*   **Anti-Corruption Layer hacia PostgreSQL en [PersistenceMapper.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/repository/mapper/PersistenceMapper.java):**
    *   **Lectura (`Entity -> Domain`)**:
        *   En `toDomain(MarketDataJpaEntity)` y `toDomain(OrderJpaEntity)`: envolver cada `BigDecimal` en `MonetaryAmount.ars(...)` (dado que *"Los precios de los activos tienen que estar en pesos"*).
    *   **Escritura (`Domain -> Entity`)**:
        *   En `toEntity(Order)`: desencapsular `domain.getPrice().amount()` hacia la columna `NUMERIC` de `OrderJpaEntity`.
*   **Mapeo Web en [ApiMapper.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/controller/mapper/ApiMapper.java):**
    *   En `toApi(Portfolio)`: mapear `portfolio.user()` hacia `UserSummaryApi.builder().id(user.id()).accountNumber(user.accountNumber()).build()` bajo la propiedad `user`, y traducir `totalAccountValue` y `availableCash` al `MonetaryAmount` del contrato.
    *   En `toApi(AssetPosition)`: mapear `position.instrument()` hacia `InstrumentSummaryApi.builder().id(inst.id()).ticker(inst.ticker()).build()` bajo la propiedad `instrument` (únicamente `id` y `ticker`), y traducir `lastPrice` y `marketValue` al `MonetaryAmount` del contrato.
    *   En `toApi(InstrumentQuote)`, `toApi(Order)` y `toCommand(...)`: traducir entre el `MonetaryAmount` del dominio (`com.cocos.trading.domain.model.MonetaryAmount`) y el del contrato (`com.cocos.trading.api.common.MonetaryAmount`).
*   **Validación en [OrderCreateRequestValidator.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/controller/validator/OrderCreateRequestValidator.java) y [ValidatorUtils.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/controller/validator/ValidatorUtils.java):**
    *   Validar que `amount` y `price` (cuando estén presentes) contengan un `amount > 0` y `currency = "ARS"`, rechazando con `400 Bad Request` (`BadRequestProblem`) importes no positivos o divisas inválidas/ausentes.
*   **Actualización de Suites de Test (`Unit` + `Integration`):**
    *   Actualizar `TradingFixtures`, `DomainModelTest`, `OrderServiceTest`, `PortfolioServiceTest`, `InstrumentServiceTest`, `OrderControllerIT` y `PortfolioAndInstrumentControllerIT` (validando los JSON paths `$.user.id`, `$.user.accountNumber`, `$.positions[0].instrument.id`, `$.positions[0].instrument.ticker`, `$.totalAccountValue.amount`, `$.totalAccountValue.currency`, etc.).

---

### 4. Actualización de Postman Collection y `README.md` (Design Decisions)
*   **Colección Postman ([cocos-trading-api.postman_collection.json](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-api.postman_collection.json)):**
    *   Actualizar los *requests* de creación de órdenes (`Submit MARKET BUY Order (By Total ARS Investment Amount)` con `"amount": { "amount": 15000.00, "currency": "ARS" }` y `Submit LIMIT BUY Order` con `"price": { "amount": 900.00, "currency": "ARS" }`).
    *   Actualizar la documentación/ejemplos de respuesta esperada de `1. Get User Portfolio` (`GET /portfolios/1`) y demás endpoints para reflejar la estructura consolidada final con `"user": { "id", "accountNumber" }`, `"instrument": { "id", "ticker" }` y `MonetaryAmount`.
*   **Documentación Arquitectónica ([README.md](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/README.md)):**
    *   Agregar la **Sección `3.8. Money Pattern (`MonetaryAmount`) y Agrupamiento Semántico de Entidades (`user` & `instrument`)`** en *"3. Decisiones de Diseño / Design Decisions"*:
        *   **Money Pattern & Erradicación de *Primitive Obsession* (`MonetaryAmount`)**: Explicar el uso de `MonetaryAmount(amount, currency)` tanto en el contrato como en el dominio para prevenir *currency mismatch* y preparar la API para soporte multimoneda, resolviendo la persistencia en `PersistenceMapper` para preservar intacto el esquema SQL original del evaluador.
        *   **Agrupamiento Semántico de Entidades (`user` e `instrument`) y Cohesión del Contrato (DX)**: Explicar de forma concisa cómo agrupar los datos del titular bajo `"user": { "id", "accountNumber" }` en la raíz del Portfolio y los datos clave de identificación del activo bajo `"instrument": { "id", "ticker" }` en cada posición evita respuestas planas desacopladas, mantiene el *payload* liviano y eleva la **Developer Experience (DX)** para clientes frontend/móviles.
    *   Actualizar los ejemplos cURL y bloques JSON de respuesta en la sección `6. API Endpoints & Ejemplos cURL`.
