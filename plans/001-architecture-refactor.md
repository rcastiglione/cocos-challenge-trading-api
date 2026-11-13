# Java Code Refactoring Plan

This document outlines the step-by-step refactoring plan to elevate the code quality, enforce a **100% framework-agnostic contract module**, decouple the domain model via polymorphism, and localize/enhance the technical documentation of the **Cocos Challenge Trading API**.

---

## Step 0: Framework-Agnostic `cocos-trading-contract` Module

To make `cocos-trading-contract.jar` purely portable and consumable by any downstream microservice regardless of runtime framework (**Spring Boot**, **Quarkus**, or **Micronaut**):

### 0.1 Strip Framework Dependencies from `cocos-trading-contract/pom.xml`
- **Remove**:
  - `org.springframework.boot:spring-boot-starter-validation`
  - `org.springframework:spring-web`
- **Keep Only Agnostic Dependencies**:
  - `org.projectlombok:lombok` (`<scope>provided</scope>` — compile-time only, zero runtime footprint).
  - `com.fasterxml.jackson.core:jackson-annotations` (industry-standard JSON serialization metadata).
  - `io.swagger.core.v3:swagger-annotations-jakarta` (framework-agnostic OpenAPI 3 specification metadata).

### 0.2 Decouple Contract Interfaces & DTOs from Spring / Jakarta Validation
- **Contract Interfaces (`InstrumentContractApi.java`, `OrderContractApi.java`, `PortfolioContractApi.java`)**:
  - Remove all `org.springframework.web.bind.annotation.*` (`@RequestMapping`, `@GetMapping`, `@PostMapping`, `@RequestBody`, `@PathVariable`, `@ModelAttribute`, `@ResponseStatus`), `org.springframework.http.HttpStatus`, and `jakarta.validation.Valid` imports and annotations from the contract interfaces.
  - Retain only pure Java method signatures and OpenAPI `@Tag`, `@Operation`, and `@Parameter` annotations from `io.swagger.v3.oas.annotations.*`.
- **Request & Response DTOs (`OrderCreateRequestApi.java`, `InstrumentSearchFilterRequestApi.java`, `OrderApi.java`, `InstrumentApi.java`, `PortfolioApi.java`, `AssetPositionApi.java`)**:
  - Ensure zero validation annotations (`@NotNull`, `@Min`, etc.) or Spring imports exist in the DTOs—keeping only `@Builder` (Lombok), Jackson annotations, and Swagger `@Schema` metadata.

### 0.3 Move Web Routing & Input Validation Exclusively to `cocos-trading-service`
- **REST Controllers (`OrderController.java`, `InstrumentController.java`, `PortfolioController.java`)**:
  - Declare `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@ResponseStatus`, `@PathVariable`, `@ModelAttribute`, and `@Valid @RequestBody` directly on the concrete Spring controller classes in `cocos-trading-service`.
- **Explicit Spring Validators via `@InitBinder`**:
  - Perform all input validation in `cocos-trading-service` using dedicated Spring `Validator` implementations (`OrderCreateRequestValidator` and `ValidatorUtils`) registered via `@InitBinder` inside `OrderController`.

---

## Step 1: Reduce Boilerplate with Lombok

### 1.1 Maven Configuration (`pom.xml`, `cocos-trading-contract/pom.xml`, `cocos-trading-service/pom.xml`)
- **Root `pom.xml`**:
  - Leverage the `org.projectlombok:lombok` version managed by `spring-boot-starter-parent` (`3.3.0`).
  - Configure `annotationProcessorPaths` inside `maven-compiler-plugin` in `<pluginManagement>` so annotation processing works seamlessly on Java 21 across both modules.
- **Submodules (`cocos-trading-contract/pom.xml` & `cocos-trading-service/pom.xml`)**:
  - Add `org.projectlombok:lombok` with `<scope>provided</scope>`.
  - In `cocos-trading-service/pom.xml`, exclude Lombok from the `spring-boot-maven-plugin` repackage step so it is not bundled into the fat JAR.

### 1.2 JPA Entities (`com.cocos.trading.repository.entity.*`)
- **Target files**: `OrderJpaEntity.java`, `InstrumentJpaEntity.java`, `MarketDataJpaEntity.java`, `UserJpaEntity.java`.
- Remove all manual getters, setters, no-arg constructors, and all-args constructors.
- Annotate with `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, and `@Builder` (specifically avoiding `@Data` on `@Entity` classes to prevent JPA `equals`/`hashCode`/`toString` proxy traps).

### 1.3 Domain Models (`com.cocos.trading.domain.model.*`) & Application Models (`com.cocos.trading.application.port.in.model.*`)
- **Polymorphic `Order` hierarchy** (detailed in Step 2): Use `@Getter`, `@ToString`, `@EqualsAndHashCode`, and `@SuperBuilder(toBuilder = true)` across the abstract base class `Order` and concrete subclasses `TradeOrder` and `TransferOrder`.
- **Other Domain Models & Commands/Queries** (`AssetPosition.java`, `Instrument.java`, `InstrumentQuote.java`, `MarketData.java`, `Portfolio.java`, `User.java`, `PlaceOrderCommand.java`, `InstrumentSearchQuery.java`):
  - Annotate with `@Builder` to simplify construction in services, mappers, and test fixtures.

### 1.4 Contract DTOs (`com.cocos.trading.api.*`) & Service Beans
- **DTOs** (`OrderCreateRequestApi.java`, `InstrumentSearchFilterRequestApi.java`, `OrderApi.java`, `InstrumentApi.java`, `PortfolioApi.java`, `AssetPositionApi.java`):
  - Annotate with `@Builder` for clean fluent instantiation in `ApiMapper.java` and tests.
- **Services, Adapters & Controllers**:
  - Replace manual constructors and `LoggerFactory.getLogger(...)` declarations in services (`OrderService.java`, `PortfolioService.java`, `InstrumentService.java`), controllers, exception handlers, and repository adapters with `@RequiredArgsConstructor` and `@Slf4j`.

---

## Step 2: Domain Model Refactoring (Polymorphism)

### 2.1 Abstract Base Class: `Order.java`
Convert `Order` from a `record` into an `abstract class` annotated with `@Getter`, `@ToString`, `@EqualsAndHashCode`, and `@SuperBuilder(toBuilder = true)`:
- **Shared Fields**:
  - `id` (`Long`)
  - `instrumentId` (`Long`)
  - `userId` (`Long`)
  - `size` (`Integer`)
  - `price` (`BigDecimal`)
  - `type` (`OrderType`)
  - `side` (`OrderSide`)
  - `status` (`OrderStatus`)
  - `datetime` (`LocalDateTime`)
- **Shared & Polymorphic Behavior**:
  - `getTotalAmount()`: Computes `price * size` scaled to 2 decimal places.
  - `calculateMaxShareSize(BigDecimal amount, BigDecimal unitPrice)`: Static helper for integral share/unit sizing.
  - `public abstract Order cancel();`: Polymorphic state transition method returning a new/updated instance of the concrete subclass with `OrderStatus.CANCELLED` (preserving immutability via `toBuilder().status(OrderStatus.CANCELLED).build()`).
  - `public abstract BigDecimal getCashImpact();`: Polymorphic helper returning the signed ARS cash delta (`+` for `CASH_IN` / `SELL`, `-` for `CASH_OUT` / `BUY`).

### 2.2 Concrete Subclasses (`com.cocos.trading.domain.model`)
1. **`TradeOrder extends Order`**:
   - Represents market operations on equity instruments (`ACCIONES`).
   - Enforces domain invariants in its constructor/builder: `side` must be `BUY` or `SELL` (`!side.isCashOperation()`), and `type` must be `MARKET` or `LIMIT`.
   - Implements `cancel()` returning a `TradeOrder` with `status = OrderStatus.CANCELLED`.
2. **`TransferOrder extends Order`**:
   - Represents cash movements on currency instruments (`MONEDA`).
   - Enforces domain invariants: `side` must be `CASH_IN` or `CASH_OUT` (`side.isCashOperation()`), and defaults/normalizes `price` to `BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP)` (`1.00` ARS).
   - Implements `cancel()` returning a `TransferOrder` with `status = OrderStatus.CANCELLED`.

### 2.3 Strategy & Calculator Updates
- **`AbstractEquityOrderExecutionStrategy.java`**: Instantiate and return `TradeOrder.builder()...build()`.
- **`CashTransferExecutionStrategy.java`**: Instantiate and return `TransferOrder.builder()...build()`.
- **`PortfolioService.java` & `AccountLedgerCalculator.java`**: Leverage `TradeOrder` vs `TransferOrder` polymorphism (e.g., `PortfolioService` only accumulates positions for `TradeOrder` instances, and `AccountLedgerCalculator` delegates cash impact to `order.getCashImpact()`).

### 2.4 Discriminator Logic in `PersistenceMapper.java`
- **`toDomain(OrderJpaEntity entity)`**:
  - Inspects `entity.getSide()` as the discriminator:
    - When `side` is `BUY` or `SELL` (`!entity.getSide().isCashOperation()`), instantiates and returns a `TradeOrder` via `TradeOrder.builder()...build()`.
    - When `side` is `CASH_IN` or `CASH_OUT` (`entity.getSide().isCashOperation()`), instantiates and returns a `TransferOrder` via `TransferOrder.builder()...build()`.
- **`toEntity(Order domain)`**:
  - Maps the common hierarchy fields from the abstract `Order` reference directly into `OrderJpaEntity.builder()...build()`, keeping the single-table persistence transparent to the domain layer.

---

## Step 3: `README.md` Localization (Spanish / LatAm Fintech Spanglish)

Plan a full rewrite of `README.md` with a clear, natural Spanish narrative tailored for reviewers while keeping ubiquitous domain and architectural terms in English (`TradeOrder`, `TransferOrder`, `Order`, `Equity`, `Ticker`, `Cash`, `Market`, `Limit`, `Side`, `Status`, `Portfolio`, `Ledger`, `Weighted Average Cost (WAC)`, `Endpoints`, `DTOs`, `Boilerplate`, `Hexagonal Architecture`, `Strategy Pattern`).

Planned structure for `README.md`:
1. **Título & Descripción General**: Introducción al motor transaccional de trading (*Transactional Trading Engine API*) construido con **Java 21**, **Spring Boot 3.3**, **Lombok** y **Hexagonal Architecture** multi-módulo con un contrato 100% *framework-agnostic*.
2. **Tech Stack**: Detalle de tecnologías en español manteniendo nombres técnicos en inglés (*Records*, *Pattern Matching*, *Spring Data JPA*, *Zalando Problem RFC 7807*, *SpringDoc OpenAPI / Swagger UI*, *Lombok*, *Surefire / Failsafe / JaCoCo*).
3. **Arquitectura Multi-Módulo & Hexagonal**: Explicación de la separación entre `cocos-trading-contract` (contrato puro y *framework-agnostic* sin dependencias de Spring: `DTOs`, interfaces `*ContractApi`) y `cocos-trading-service` (`Domain`, `Application Ports & Use Cases`, `Execution Strategies`, `Controllers & Spring Validators`, `JPA Repositories`).
4. **Decisiones de Diseño / Design Decisions**: (Detallado en el Paso 4).
5. **Cómo Compilar, Ejecutar Tests y Correr Localmente**: Instrucciones paso a paso (`mvn clean verify`, `spring-boot:run`, links a *Swagger UI* y *Postman Collection*).
6. **API Endpoints & Ejemplos cURL**: Documentación en español de cada *endpoint* (`/portfolios/{userId}`, `/instruments`, `POST /orders`, `POST /orders/{id}/cancel`) con payloads de ejemplo para órdenes `MARKET`, `LIMIT` (por `size` y por `amount`), y movimientos de `CASH_IN` / `CASH_OUT`.

---

## Step 4: Design Decisions Documentation (`README.md`)

Add a dedicated, in-depth section titled **`## Decisiones de Diseño / Design Decisions`** in `README.md`. In addition to documenting the *Framework-Agnostic Contract*, the *Strategy Pattern* (`OrderExecutionStrategy`), el cálculo de `size` vs `amount`, la persistencia de órdenes `REJECTED` y la valuación del *Portfolio* con *Weighted Average Cost (WAC)*, this section will prominently feature:

### Polimorfismo en el Dominio vs. Unified Ledger en Persistencia (`TradeOrder` vs `TransferOrder`)
- **Contexto & Problema**:
  - El esquema SQL provisto en el challenge define una única tabla `orders` que almacena tanto operaciones de mercado sobre activos financieros (`BUY` / `SELL` sobre instrumentos tipo `ACCIONES` con tipos `MARKET` / `LIMIT`) como movimientos de fondeo y retiro de dinero (`CASH_IN` / `CASH_OUT` sobre instrumentos tipo `MONEDA` a precio unitario fijo de `1.00` ARS).
  - Modelar ambos conceptos en una única clase plana dentro del *Domain Layer* acopla reglas de negocio completamente distintas (por ejemplo: validación de *buying power* vs tenencia de *shares*, precios de mercado/límite vs paridad `1:1` de `Cash`, o impacto en el cálculo de *Weighted Average Cost*).
- **Solución Arquitectónica**:
  - **Persistencia (*Unified Ledger* en tabla `orders`)**: Se mantiene intacta la tabla única `orders` mapeada por `OrderJpaEntity`. Esto preserva la compatibilidad total con el esquema SQL inicial y permite consultar el libro mayor (*Account Ledger*) de un usuario en una sola query indexada para calcular su `availableCash` y tenencias (`positions`).
  - **Dominio Desacoplado por Herencia (`Order` -> `TradeOrder` | `TransferOrder`)**: En la capa de dominio creamos la clase base abstracta `Order` y dos subclases concretas:
    - `TradeOrder`: Encapsula exclusivamente operaciones de mercado (`BUY`/`SELL`, `MARKET`/`LIMIT`) sobre `Equity` (`ACCIONES`).
    - `TransferOrder`: Encapsula movimientos de caja (`CASH_IN`/`CASH_OUT`) sobre `Cash` (`MONEDA`).
  - **Discriminador en `PersistenceMapper`**: El componente `PersistenceMapper` actúa como *Anti-Corruption Layer / Discriminator* entre la base de datos y el dominio, inspeccionando el campo `side` de `OrderJpaEntity` para instanciar polimórficamente un `TradeOrder` o un `TransferOrder`.
- **Pros & Contras (Trade-offs)**:
  - **Pros**:
    1. *Separation of Concerns & Clean Domain*: Cada subclase protege sus propios invariantes de negocio sin contaminar a la otra con campos o validaciones irrelevantes.
    2. *Performance en Lectura (Unified Ledger)*: Evita `JOIN`s o `UNION`s costosos entre múltiples tablas al reconstruir el *Portfolio* y el saldo disponible (`availableCash`) a partir de las órdenes `FILLED`.
    3. *Zero Schema Migration*: Respeta 100% el modelo relacional provisto por el challenge sin requerir columnas discriminadoras adicionales ni migraciones DDL.
  - **Contras**:
    1. *Complejidad de Mapeo en Infraestructura*: El `PersistenceMapper` asume la responsabilidad de discriminar manualmente el subtipo en base al campo `side` en lugar de delegarlo automáticamente a JPA (`@Inheritance` / `@DiscriminatorColumn`).
    2. *Columnas Compartidas en DB*: La tabla `orders` retiene semánticas ligeramente sobrecargadas a nivel físico (por ejemplo, `size` representa cantidad de acciones en `TradeOrder` y unidades monetarias en `TransferOrder`), lo cual debe ser mitigado por el mapeo hacia el dominio.
