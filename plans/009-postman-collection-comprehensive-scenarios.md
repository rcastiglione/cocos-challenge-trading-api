# Spec: Colección de Postman Exhaustiva y Documentación Viva del Contrato Polimórfico (`OrderCreateRequestApi` / `OrderApi`)
**Status:** Completed (Plan 009 — Build Green + 4-Folder Comprehensive Postman Collection)
**Context:** Cocos Challenge Trading API
**Architecture:** Multi-Module Pragmatic Service-Driven Architecture (Java 21, Spring Boot 3.3, Jackson Polymorphic Contract `@JsonTypeInfo`/`@JsonSubTypes`, Zalando Problem RFC 7807)

## Objetivo
Transformar [cocos-trading-api.postman_collection.json](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-api.postman_collection.json) en una **documentación viva, estructurada y de nivel productivo Fintech** (al estándar de plataformas como **InvertirOnline (IOL)**, **Balanz** o **Cocos Capital**), reflejando fielmente el rediseño polimórfico del contrato público introducido en el Plan 008:

1. **Contratos Polimórficos de Entrada (`OrderCreateRequestApi`) y Salida (`OrderApi`)**:
   - **Operaciones Bursátiles (`BUY` / `SELL` $\rightarrow$ [TradeOrderRequestApi.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-contract/src/main/java/com/cocos/trading/api/request/TradeOrderRequestApi.java) y [TradeOrderApi.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-contract/src/main/java/com/cocos/trading/api/response/TradeOrderApi.java))**: Exigen `userId`, `instrumentId`, `side` (`BUY` | `SELL`), `type` (`MARKET` | `LIMIT`), y dimensionamiento XOR mediante `quantity` (acciones enteras) o `amount` (`MonetaryAmount`), más `price` (`MonetaryAmount`) obligatorio para órdenes `LIMIT`. La respuesta expone `user`, `instrument`, `quantity`, `price`, `type`, `side`, `status` y `datetime` (omitiendo `amount`).
   - **Transferencias Monetarias (`CASH_IN` / `CASH_OUT` $\rightarrow$ [TransferOrderRequestApi.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-contract/src/main/java/com/cocos/trading/api/request/TransferOrderRequestApi.java) y [TransferOrderApi.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-contract/src/main/java/com/cocos/trading/api/response/TransferOrderApi.java))**: Exigen únicamente `userId`, `side` (`CASH_IN` | `CASH_OUT`) y `amount` (`MonetaryAmount` con `currency = "ARS"`). Resuelven internamente el instrumento `MONEDA` (`ARS`, `id = 66`) y retornan simétricamente `id`, `user`, `amount`, `side`, `status` (`FILLED`) y `datetime` (omitiendo `instrument`, `type`, `quantity` y `price`).
2. **Cobertura Exhaustiva de Casos de Validación y Reglas de Negocio ("Sad Paths")**:
   - Documentar con ejemplos de `request` y `response` (bajo **RFC 7807 Problem Details** y estados de dominio `REJECTED`) todos los escenarios de rechazo validados por [OrderCreateRequestValidator.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/controller/validator/OrderCreateRequestValidator.java), Jackson (`@JsonIgnoreProperties(ignoreUnknown = false)`), el motor de ejecución ([AbstractEquityOrderExecutionStrategy.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/strategy/AbstractEquityOrderExecutionStrategy.java), [CashTransferExecutionStrategy.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/strategy/CashTransferExecutionStrategy.java)) y el control de idempotencia en Redis.
3. **Organización Modular en Carpetas (Developer & QA Experience)**:
   - Estructurar la colección en **4 carpetas temáticas** claramente delimitadas para facilitar el onboarding y las pruebas exploratorias de ingenieros Frontend, Mobile y QA.

---

## 1. Estructura Propuesta de Carpetas en la Colección de Postman

Se reorganizará el array raíz `"item"` de [cocos-trading-api.postman_collection.json](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-api.postman_collection.json) en 4 carpetas lógicas con descripciones detalladas:

```text
Cocos Capital Trading Engine API
├── 📁 1. Portfolios & Instruments
│   ├── 1.1 Get User Portfolio (GET /portfolios/1)
│   ├── 1.2 Search Instruments — General Query (GET /instruments?query=PAMP)
│   └── 1.3 Search Instruments — By Ticker & Name (GET /instruments?ticker=MOLI&name=Molinos)
├── 📁 2. Trade Orders (Market & Limit)
│   ├── 2.1 Submit MARKET BUY Order — By Exact Share Quantity (POST /orders)
│   ├── 2.2 Submit MARKET BUY Order — By Total ARS Investment Amount (POST /orders)
│   ├── 2.3 Submit MARKET SELL Order — By Exact Share Quantity (POST /orders)
│   ├── 2.4 Submit LIMIT BUY Order — Pending Status NEW (POST /orders)
│   └── 2.5 Cancel Pending (NEW) Order (POST /orders/5/cancel)
├── 📁 3. Cash Transfers (Deposits & Withdrawals)
│   ├── 3.1 Submit CASH_IN Transfer — ARS Deposit (POST /orders)
│   └── 3.2 Submit CASH_OUT Transfer — ARS Withdrawal (POST /orders)
└── 📁 4. Validation & Edge Cases (Sad Paths)
    ├── 4.1 Transfer — Unsupported Currency USD (400 Bad Request)
    ├── 4.2 Transfer — Invalid Unknown Field 'quantity' or 'price' (400 Bad Request)
    ├── 4.3 Market Trade — Ambiguous Sizing: Both 'quantity' and 'amount' (400 Bad Request — XOR Violation)
    ├── 4.4 Limit Trade — Missing Target 'price' (400 Bad Request)
    ├── 4.5 Cash Out — Insufficient Available Cash (409 Conflict / Business Rule Violation)
    ├── 4.6 Market Sell — Insufficient Share Holdings (201 Created — Order Status REJECTED)
    ├── 4.7 Market Buy — Insufficient Buying Power (201 Created — Order Status REJECTED)
    └── 4.8 Idempotency — Duplicate Order Retry with Static Key (409 Conflict — Duplicate Order)
```

---

## 2. Detalle de Escenarios: Happy Paths (Carpetas 1, 2 y 3)

### 2.1. Carpeta `1. Portfolios & Instruments`
Conserva los endpoints de consulta alineados con el **Money Pattern** (`MonetaryAmount`) y el agrupamiento semántico (`user`, `instrument`):
- **`1.1 Get User Portfolio` (`GET {{baseUrl}}/portfolios/1`)**: Documenta el ejemplo `200 OK` con `user: { id: 1, accountNumber: "10001" }`, `totalAccountValue`, `availableCash` y el arreglo `positions` usando `quantity` y `MonetaryAmount`.
- **`1.2 Search Instruments — General Query` (`GET {{baseUrl}}/instruments?query=PAMP`)**: Búsqueda general con ejemplo `200 OK` incluyendo `lastPrice` y `previousClose` como `MonetaryAmount`.
- **`1.3 Search Instruments — By Ticker & Name` (`GET {{baseUrl}}/instruments?ticker=MOLI&name=Molinos`)**: Búsqueda combinada por filtros específicos con ejemplo `200 OK`.

---

### 2.2. Carpeta `2. Trade Orders (Market & Limit)` — Contrato Polimórfico `TradeOrderRequestApi` $\rightarrow$ `TradeOrderApi`
Todos los requests de esta carpeta utilizan el discriminador `"side": "BUY"` o `"side": "SELL"` y el header `Idempotency-Key: {{$guid}}`:

1. **`2.1 Submit MARKET BUY Order — By Exact Share Quantity`**:
   - **Request Body (`TradeOrderRequestApi`)**:
     ```json
     {
       "userId": 1,
       "instrumentId": 47,
       "side": "BUY",
       "type": "MARKET",
       "quantity": 10
     }
     ```
   - **Saved Response (`201 Created` — `TradeOrderApi`)**: Retorna `user` (`{ "id": 1, "accountNumber": "10001" }`), `instrument` (`{ "id": 47, "ticker": "PAMP" }`), `quantity: 10`, `price: { "amount": 925.85, "currency": "ARS" }`, `type: "MARKET"`, `side: "BUY"`, `status: "FILLED"`, `datetime` (sin campo `amount`).

2. **`2.2 Submit MARKET BUY Order — By Total ARS Investment Amount`**:
   - **Request Body (`TradeOrderRequestApi`)**:
     ```json
     {
       "userId": 1,
       "instrumentId": 47,
       "side": "BUY",
       "type": "MARKET",
       "amount": {
         "amount": 15000.00,
         "currency": "ARS"
       }
     }
     ```
   - **Saved Response (`201 Created` — `TradeOrderApi`)**: Documenta cómo el motor calcula `quantity = floor(15000.00 / 925.85) = 16` acciones enteras sin fracciones y retorna `TradeOrderApi` con `quantity: 16` y `price: { "amount": 925.85, "currency": "ARS" }` (omitiendo `amount` en la respuesta).

3. **`2.3 Submit MARKET SELL Order — By Exact Share Quantity`**:
   - **Request Body (`TradeOrderRequestApi`)**:
     ```json
     {
       "userId": 1,
       "instrumentId": 47,
       "side": "SELL",
       "type": "MARKET",
       "quantity": 5
     }
     ```
   - **Saved Response (`201 Created` — `TradeOrderApi`)**: Retorna la venta ejecutada en estado `FILLED` sobre las tenencias existentes de `PAMP` (`id = 47`) del usuario `1`.

4. **`2.4 Submit LIMIT BUY Order — Pending Status NEW`**:
   - **Request Body (`TradeOrderRequestApi`)**:
     ```json
     {
       "userId": 1,
       "instrumentId": 47,
       "side": "BUY",
       "type": "LIMIT",
       "quantity": 5,
       "price": {
         "amount": 900.00,
         "currency": "ARS"
       }
     }
     ```
   - **Saved Response (`201 Created` — `TradeOrderApi`)**: Retorna la orden en estado `NEW` con el precio límite `{ "amount": 900.00, "currency": "ARS" }`.

5. **`2.5 Cancel Pending (NEW) Order` (`POST {{baseUrl}}/orders/5/cancel`)**:
   - Utiliza por defecto el `orderId = 5` (orden `LIMIT BUY` en estado `NEW` presente en el seed inicial de `docker/postgres/init.sql` y NeonDB) y documenta la respuesta `200 OK` con `status: "CANCELLED"`.

---

### 2.3. Carpeta `3. Cash Transfers (Deposits & Withdrawals)` — Contrato Polimórfico `TransferOrderRequestApi` $\rightarrow$ `TransferOrderApi`
Todos los requests de esta carpeta utilizan `"side": "CASH_IN"` o `"side": "CASH_OUT"`, enviando **únicamente** `userId`, `side` y `amount`:

1. **`3.1 Submit CASH_IN Transfer — ARS Deposit`**:
   - **Request Body (`TransferOrderRequestApi`)**:
     ```json
     {
       "userId": 1,
       "side": "CASH_IN",
       "amount": {
         "amount": 50000.00,
         "currency": "ARS"
       }
     }
     ```
   - **Saved Response (`201 Created` — `TransferOrderApi`)**:
     ```json
     {
       "id": 15,
       "user": {
         "id": 1,
         "accountNumber": "10001"
       },
       "amount": {
         "amount": 50000.00,
         "currency": "ARS"
       },
       "side": "CASH_IN",
       "status": "FILLED",
       "datetime": "2024-06-15T14:35:00"
     }
     ```

2. **`3.2 Submit CASH_OUT Transfer — ARS Withdrawal`**:
   - **Request Body (`TransferOrderRequestApi`)**:
     ```json
     {
       "userId": 1,
       "side": "CASH_OUT",
       "amount": {
         "amount": 20000.00,
         "currency": "ARS"
       }
     }
     ```
   - **Saved Response (`201 Created` — `TransferOrderApi`)**: Retorna `TransferOrderApi` con `status: "FILLED"`, `amount: { "amount": 20000.00, "currency": "ARS" }` y sin campos bursátiles (`instrument`, `type`, `quantity`, `price`).

---

## 3. Detalle de Escenarios: Validation & Edge Cases — "The Sad Paths" (Carpeta 4)

Esta carpeta documenta exhaustivamente las defensas del contrato polimórfico, el validador [OrderCreateRequestValidator.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/controller/validator/OrderCreateRequestValidator.java) y las reglas del motor transaccional:

### 3.1. `4.1 Transfer — Unsupported Currency USD (400 Bad Request)`
- **Escenario**: Un request `CASH_IN` intenta transferir dólares (`"currency": "USD"`) o euros (`"EUR"`), cuando la plataforma opera exclusivamente en pesos argentinos (`"ARS"`).
- **Componente que valida**: `OrderCreateRequestValidator` $\rightarrow$ `ValidatorUtils.validateMonetaryAmount(...)` rechaza el campo `amount` con código `amount.currency.invalid` (`"amount.currency is required and must be 'ARS'."`).
- **Request Body**:
  ```json
  {
    "userId": 1,
    "side": "CASH_IN",
    "amount": {
      "amount": 1000.00,
      "currency": "USD"
    }
  }
  ```
- **Documented Response (`400 Bad Request` — RFC 7807 Problem Details)**:
  ```json
  {
    "type": "https://api.cocos.com/problems/trading-api/bad-request/400",
    "title": "Bad Request",
    "status": 400,
    "detail": "Validation failed for request payload.",
    "parameters": {
      "amount": "amount.currency is required and must be 'ARS'."
    }
  }
  ```

### 3.2. `4.2 Transfer — Invalid Unknown Field 'quantity' or 'price' (400 Bad Request)`
- **Escenario**: Un request `CASH_IN` incluye por error campos exclusivos de operatoria bursátil como `"quantity": 100` o `"price": { "amount": 1.00, "currency": "ARS" }`.
- **Componente que valida**: Jackson deserializa `"side": "CASH_IN"` hacia [TransferOrderRequestApi.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-contract/src/main/java/com/cocos/trading/api/request/TransferOrderRequestApi.java), el cual está anotado con `@JsonIgnoreProperties(ignoreUnknown = false)`. Jackson lanza `UnrecognizedPropertyException` (`HttpMessageNotReadableException`), traducida a un `400 Bad Request` (`BadRequestProblem`).
- **Request Body**:
  ```json
  {
    "userId": 1,
    "side": "CASH_IN",
    "amount": {
      "amount": 50000.00,
      "currency": "ARS"
    },
    "quantity": 100,
    "price": {
      "amount": 1.00,
      "currency": "ARS"
    }
  }
  ```
- **Documented Response (`400 Bad Request` — RFC 7807 Problem Details)**:
  ```json
  {
    "type": "https://api.cocos.com/problems/trading-api/bad-request/400",
    "title": "Bad Request",
    "status": 400,
    "detail": "Malformed JSON request or unrecognized property 'quantity' for TransferOrderRequestApi (CASH_IN/CASH_OUT only accept 'userId', 'side', and 'amount')."
  }
  ```

### 3.3. `4.3 Market Trade — Ambiguous Sizing: Both 'quantity' and 'amount' (400 Bad Request — XOR Violation)`
- **Escenario**: Una orden `MARKET BUY` envía simultáneamente `"quantity": 10` y `"amount": { "amount": 15000.00, "currency": "ARS" }`, violando la regla de exclusión mutua (XOR).
- **Componente que valida**: `OrderCreateRequestValidator.validateTradeOrder(...)` detecta `hasQuantity && hasAmount` y rechaza el campo `quantity` con código `quantityOrAmount.ambiguous` (`"Provide either quantity or amount, not both."`).
- **Request Body**:
  ```json
  {
    "userId": 1,
    "instrumentId": 47,
    "side": "BUY",
    "type": "MARKET",
    "quantity": 10,
    "amount": {
      "amount": 15000.00,
      "currency": "ARS"
    }
  }
  ```
- **Documented Response (`400 Bad Request` — RFC 7807 Problem Details)**:
  ```json
  {
    "type": "https://api.cocos.com/problems/trading-api/bad-request/400",
    "title": "Bad Request",
    "status": 400,
    "detail": "Validation failed for request payload.",
    "parameters": {
      "quantity": "Provide either quantity or amount, not both."
    }
  }
  ```

### 3.4. `4.4 Limit Trade — Missing Target 'price' (400 Bad Request)`
- **Escenario**: Una orden `LIMIT BUY` especifica `"quantity": 10` pero omite el campo obligatorio `"price"`.
- **Componente que valida**: `OrderCreateRequestValidator.validateTradeOrder(...)` verifica `request.type() == OrderTypeApi.LIMIT && request.price() == null` y rechaza `price` con código `price.required` (`"price is required for LIMIT orders."`).
- **Request Body**:
  ```json
  {
    "userId": 1,
    "instrumentId": 47,
    "side": "BUY",
    "type": "LIMIT",
    "quantity": 10
  }
  ```
- **Documented Response (`400 Bad Request` — RFC 7807 Problem Details)**:
  ```json
  {
    "type": "https://api.cocos.com/problems/trading-api/bad-request/400",
    "title": "Bad Request",
    "status": 400,
    "detail": "Validation failed for request payload.",
    "parameters": {
      "price": "price is required for LIMIT orders."
    }
  }
  ```

### 3.5. `4.5 Cash Out — Insufficient Available Cash (409 Conflict / Business Rule Violation)`
- **Escenario**: Un retiro `CASH_OUT` solicita `99,000,000.00 ARS`, superando el saldo disponible (`availableCash`) de la cuenta comitente del usuario `1`.
- **Comportamiento del Motor**: En [CashTransferExecutionStrategy.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/strategy/CashTransferExecutionStrategy.java), los retiros de efectivo que exceden `availableCash` lanzan `BusinessRuleViolationException` (traducida a HTTP `409 Conflict` — `BusinessRuleViolationProblem` bajo RFC 7807, liberando además la clave de idempotencia en Redis tras el rollback para no contaminar el libro mayor con retiros fiduciarios fallidos). *(Nota: En la descripción del request en Postman se explicará tanto esta semántica `409 Conflict / Business Rule Violation` para `CASH_OUT` como la diferencia frente a órdenes de mercado `BUY`/`SELL` que se persisten con estado `REJECTED`).*
- **Request Body**:
  ```json
  {
    "userId": 1,
    "side": "CASH_OUT",
    "amount": {
      "amount": 99000000.00,
      "currency": "ARS"
    }
  }
  ```
- **Documented Response (`409 Conflict` — RFC 7807 `BusinessRuleViolationProblem`)**:
  ```json
  {
    "type": "https://api.cocos.com/problems/trading-api/business-rule-violation/409",
    "title": "Business Rule Violation",
    "status": 409,
    "detail": "Insufficient available cash for CASH_OUT transfer: requested=99000000.00 ARS, available=691350.00 ARS."
  }
  ```

### 3.6. `4.6 Market Sell — Insufficient Share Holdings (201 Created — Order Status REJECTED)`
- **Escenario**: Una orden `MARKET SELL` intenta vender `50,000` acciones de `PAMP` (`instrumentId = 47`), superando la posición actual del usuario `1`.
- **Comportamiento del Motor**: Siguiendo el requerimiento del challenge para órdenes de mercado, [AbstractEquityOrderExecutionStrategy.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/strategy/AbstractEquityOrderExecutionStrategy.java) detecta `context.availableShares() < resolvedQuantity`, asigna `OrderStatus.REJECTED`, **persiste la orden en la base de datos para trazabilidad de auditoría**, y la retorna con código `201 Created` y `"status": "REJECTED"`.
- **Request Body**:
  ```json
  {
    "userId": 1,
    "instrumentId": 47,
    "side": "SELL",
    "type": "MARKET",
    "quantity": 50000
  }
  ```
- **Documented Response (`201 Created` — `TradeOrderApi` con `status: "REJECTED"`)**:
  ```json
  {
    "id": 18,
    "user": {
      "id": 1,
      "accountNumber": "10001"
    },
    "instrument": {
      "id": 47,
      "ticker": "PAMP"
    },
    "quantity": 50000,
    "price": {
      "amount": 925.85,
      "currency": "ARS"
    },
    "type": "MARKET",
    "side": "SELL",
    "status": "REJECTED",
    "datetime": "2024-06-15T14:40:00"
  }
  ```

### 3.7. `4.7 Market Buy — Insufficient Buying Power (201 Created — Order Status REJECTED)`
- **Escenario**: Una orden `MARKET BUY` intenta comprar `50,000` acciones de `PAMP` (`46,292,500.00 ARS`), superando el `availableCash` del usuario `1`.
- **Comportamiento del Motor**: Se evalúa en `AbstractEquityOrderExecutionStrategy`, se persiste para auditoría con estado `REJECTED` y se retorna `TradeOrderApi` con `"status": "REJECTED"`.
- **Request Body**:
  ```json
  {
    "userId": 1,
    "instrumentId": 47,
    "side": "BUY",
    "type": "MARKET",
    "quantity": 50000
  }
  ```
- **Documented Response (`201 Created` — `TradeOrderApi` con `status: "REJECTED"`)**:
  ```json
  {
    "id": 19,
    "user": {
      "id": 1,
      "accountNumber": "10001"
    },
    "instrument": {
      "id": 47,
      "ticker": "PAMP"
    },
    "quantity": 50000,
    "price": {
      "amount": 925.85,
      "currency": "ARS"
    },
    "type": "MARKET",
    "side": "BUY",
    "status": "REJECTED",
    "datetime": "2024-06-15T14:41:00"
  }
  ```

### 3.8. `4.8 Idempotency — Duplicate Order Retry with Static Key (409 Conflict — Duplicate Order)`
- **Escenario**: Protección contra *double-spending* y reintentos de red enviando un UUID estático en el header `Idempotency-Key: 123e4567-e89b-12d3-a456-426614174000`.
- **Documented Response (`409 Conflict` — RFC 7807 `Duplicate Order`)**:
  ```json
  {
    "type": "https://api.cocos.com/problems/trading-api/business-rule-violation/409",
    "title": "Duplicate Order",
    "status": 409,
    "detail": "An order with Idempotency-Key '123e4567-e89b-12d3-a456-426614174000' has already been processed or is currently in progress.",
    "parameters": {
      "idempotencyKey": "123e4567-e89b-12d3-a456-426614174000"
    }
  }
  ```

---

## 4. Plan de Ejecución y Verificación (Tras Aprobación del Plan)

1. **Reestructuración de [cocos-trading-api.postman_collection.json](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-api.postman_collection.json)**:
   - Reemplazar la lista plana actual de 10 ítems por la jerarquía de **4 carpetas** (`1. Portfolios & Instruments`, `2. Trade Orders (Market & Limit)`, `3. Cash Transfers (Deposits & Withdrawals)`, `4. Validation & Edge Cases (Sad Paths)`).
   - Incluir descripciones Markdown ricas en cada carpeta y en cada request, explicando los invariantes de negocio, las reglas del discriminador polimórfico `side` y los códigos de respuesta esperados.
   - Incluir bloques `"response"` de ejemplo en cada uno de los requests (tanto *Happy Paths* como *Sad Paths*) para que Postman renderice la documentación offline sin siquiera necesitar levantar el servidor.
2. **Validación Sintáctica del JSON**:
   - Verificar que el archivo [cocos-trading-api.postman_collection.json](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-api.postman_collection.json) sea un JSON 100% válido conforme al esquema Postman Collection v2.1.0 (`https://schema.getpostman.com/json/collection/v2.1.0/collection.json`).
3. **Sincronización con [README.md](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/README.md) (Opcional si se requiere)**:
   - Actualizar la referencia a la colección de Postman en la sección `4.5` del `README.md` mencionando la nueva organización en 4 carpetas y los escenarios de validación incluidos.

