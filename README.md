# Cocos Capital — Transactional Trading Engine API

A production-grade RESTful API for the Cocos Capital Backend Challenge, built with **Java 21**, **Spring Boot 3.3**, and a **Multi-Module Pragmatic Hexagonal Architecture**.

---

## 1. Tech Stack

- **Java 21** (Records, Pattern Matching, Switch Expressions)
- **Spring Boot 3.3.0** (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`)
- **PostgreSQL** (NeonDB cloud PostgreSQL for runtime; H2 in-memory database for isolated integration testing)
- **Zalando Problem (`problem-spring-web-starter`)** for standardized RFC 7807 error responses
- **SpringDoc OpenAPI 2.5 (Swagger UI)** for interactive API documentation
- **Maven (Multi-Module)** with **Surefire** (unit tests), **Failsafe** (`src/it` integration tests), and **JaCoCo** code coverage

---

## 2. Multi-Module & Hexagonal Architecture

The repository is structured into two Maven modules to strictly isolate the public API contract from the core service implementation:

```text
cocos-challenge-trading-api/
├── pom.xml                                  # Parent / Aggregator POM
├── cocos-trading-contract/                  # Submodule 1: Pure API Contract (DTOs, *ContractApi interfaces, Enums)
└── cocos-trading-service/                   # Submodule 2: Core Trading Engine Service
    └── src/
        ├── main/java/com/cocos/trading/
        │   ├── domain/                      # Pure Java 21 domain models & domain exceptions
        │   ├── application/                 # Inbound/Outbound ports, Use Case services & Order Execution Strategies
        │   ├── controller/                  # REST Controllers, Spring Validators, ApiMapper & GlobalExceptionHandler
        │   ├── repository/                  # JPA Adapters, JPA Entities, Spring Data Repositories & Specifications
        │   └── config/                      # OpenAPI / Swagger configuration
        ├── test/java/com/cocos/trading/     # Unit Tests (JUnit 5 + Mockito)
        └── it/java/com/cocos/trading/       # Functional Integration Tests (@SpringBootTest + MockMvc)
```

### Key Design Decisions & Assumptions
1. **Order Routing via Strategy Pattern (`OrderExecutionStrategy`)**:
   - `MarketOrderExecutionStrategy`: Evaluates `MARKET` equity (`ACCIONES`) orders using the latest `MarketData.close` price and immediately transitions valid orders to `FILLED`.
   - `LimitOrderExecutionStrategy`: Evaluates `LIMIT` equity (`ACCIONES`) orders using the user-supplied `price` and transitions valid orders to `NEW`.
   - `CashTransferExecutionStrategy`: Handles `CASH_IN` and `CASH_OUT` transfers on `MONEDA` (ARS) instruments at unit price `1.00`.
2. **Order Sizing (`size` vs. `amount`)**:
   - Callers can specify either exact share quantity (`size`) or total investment amount in ARS (`amount`).
   - When `amount` is provided, the engine calculates `size = floor(amount / executionPrice)` via `BigDecimal.divideToIntegralValue`, guaranteeing zero fractional shares and never exceeding the requested ARS amount.
3. **Rejected Orders Persistence (`REJECTED`)**:
   - If a user lacks sufficient ARS buying power (`availableCash < size * price`), lacks sufficient shares for a `SELL` (`availableShares < size`), or supplies an `amount` smaller than 1 share (`size == 0`), the order is **persisted** in the `orders` table with status `REJECTED` and returned in the response.
4. **Portfolio Valuation & Return Calculation**:
   - **Available Cash (`availableCash`)**: Derived from all `FILLED` orders (`+CASH_IN`, `-CASH_OUT`, `-BUY (size * price)`, `+SELL (size * price)`).
   - **Total Return (%)**: Calculated per active position using the Weighted Average Cost (WAC) basis of `FILLED` purchases vs. the latest `MarketData.close` price:
     $$\text{Total Return (\%)} = \left(\frac{\text{lastPrice} - \text{averageCost}}{\text{averageCost}}\right) \times 100$$
   - **Daily Return (%)**: Calculated from the latest `MarketData` entry:
     $$\text{Daily Return (\%)} = \left(\frac{\text{close} - \text{previousClose}}{\text{previousClose}}\right) \times 100$$

---

## 3. How to Build, Test & Run

### Run Unit & Functional Integration Tests
```bash
mvn clean verify
```
- **Unit tests** (`*Test.java` in `src/test/java`) run via `maven-surefire-plugin`.
- **Functional integration tests** (`*IT.java` in `src/it/java`) run via `maven-failsafe-plugin` against an isolated in-memory database.
- **JaCoCo coverage report** is generated at `cocos-trading-service/target/site/jacoco/index.html`.

### Run the Application Locally
```bash
mvn -pl cocos-trading-service spring-boot:run
```

### Interactive Swagger UI
Once running, visit:
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `http://localhost:8080/v3/api-docs`
- **Postman Collection**: Import `cocos-trading-api.postman_collection.json` at the root of the repository.

---

## 4. API Endpoints & cURL Examples

### 1. Get User Portfolio
```bash
curl --location 'http://localhost:8080/portfolios/1'
```

### 2. Search Market Instruments (by Ticker and/or Name)
```bash
# General query matching ticker OR name
curl --location 'http://localhost:8080/instruments?query=PAMP'

# Specific ticker and/or name filters
curl --location 'http://localhost:8080/instruments?ticker=MOLI&name=Molinos'
```

### 3. Submit an Order to the Market (`POST /orders`)

#### A. Market Buy Order by Exact Share Quantity (`size`)
```bash
curl --location 'http://localhost:8080/orders' \
--header 'Content-Type: application/json' \
--data '{
    "userId": 1,
    "instrumentId": 47,
    "side": "BUY",
    "type": "MARKET",
    "size": 10
}'
```

#### B. Market Buy Order by Total ARS Investment (`amount`)
```bash
curl --location 'http://localhost:8080/orders' \
--header 'Content-Type: application/json' \
--data '{
    "userId": 1,
    "instrumentId": 47,
    "side": "BUY",
    "type": "MARKET",
    "amount": 15000.00
}'
```

#### C. Limit Buy Order (`NEW` status)
```bash
curl --location 'http://localhost:8080/orders' \
--header 'Content-Type: application/json' \
--data '{
    "userId": 1,
    "instrumentId": 47,
    "side": "BUY",
    "type": "LIMIT",
    "size": 5,
    "price": 900.00
}'
```

### 4. Cancel a Pending (`NEW`) Order
```bash
curl --location --request POST 'http://localhost:8080/orders/105/cancel'
```

