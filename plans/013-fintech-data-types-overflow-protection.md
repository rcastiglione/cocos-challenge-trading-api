# Plan 013: Fintech Data Types, Integer Overflow Protection (`quantity` -> `Long`) & High-Nominality Risks

**Status:** Pending Approval (Design & Planning Only — Zero Code Changes Applied)
**Context:** Cocos Challenge Trading API
**Architecture:** Multi-Module Pragmatic Service-Driven Architecture (Java 25 LTS, Spring Boot 4.x, PostgreSQL 16 + Flyway, Redis 7, Hibernate `SINGLE_TABLE`)

---

## 1. Gentle AI & Engram ReAct Loop — Análisis & Diagnóstico de Riesgos Fintech

### Thought (Diagnóstico del Estado Actual)
Al realizar la revisión arquitectónica final enfocada en **Data Types** y **High-Nominality Risks** (crítico en economías inflacionarias y mercados de alta nominalidad como Argentina), detectamos los siguientes puntos de falla potencial en el *codebase*:

1. **Riesgo Crítico de Integer Overflow en `quantity` / `size`**:
   - Actualmente, la cantidad de nominales/acciones (`quantity` en Java, mapeada a la columna `size` en PostgreSQL) está tipada como `Integer` / `int` en toda la cadena de ejecución:
     - **PostgreSQL (`V1__init_schema.sql`)**: `orders.size INTEGER` (`int4`, máximo `2,147,483,647`).
     - **Contrato API (`cocos-trading-contract`)**: `TradeOrderRequestApi`, `TradeOrderApi`, `AssetPositionApi` declaran `Integer quantity`.
     - **Capa de Aplicación & Dominio (`cocos-trading-service`)**: `PlaceOrderCommand`, `AssetPosition`, `OrderJpaEntity`, `AccountLedgerCalculator`, `PortfolioService.PositionAccumulator`, `AbstractEquityOrderExecutionStrategy`, `CashTransferExecutionStrategy` y `MonetaryAmount.multiply(int)`.
   - **Impacto Real en el Mercado Argentino**: En Java, `Integer.MAX_VALUE` es `2,147,483,647` (~2.14 billones en escala corta / ~2.14 mil millones). En el mercado argentino, inversores institucionales o *whales* operando *penny stocks* de bajo valor unitario (ej. **COME** — *Sociedad Comercial del Plata*) o bonos soberanos altamente líquidos (ej. **AL30** / **AL29**) pueden acumular posiciones o enviar órdenes que superen los `2.14 * 10^9` nominales (o bien operar `CASH_IN`/`CASH_OUT` de alto volumen donde `resolvedUnits` mapea pesos a `size`).
   - Superar `Integer.MAX_VALUE` produce un **Integer Overflow** silencioso en aritmética primitiva (`int += ...` -> valores negativos corruptos en el *ledger* o *WAC*) o excepciones `ArithmeticException` / `PSQLException: integer out of range` en persistencia.

2. **Documentación de Riesgos SRE & Escala Monetaria Futura (`README.md`)**:
   - El `README.md` actual no documenta el *rationale* financiero detrás de los tipos numéricos elegidos ni advierte sobre el techo de precisión/escala en esquemas SQL heredados (`NUMERIC(10, 2)` en `V1__init_schema.sql`, o el estándar clásico `NUMERIC(19, 2)`).

---

## 2. Objetivo 1: Integer Overflow Protection (`Integer` -> `Long` / `BIGINT`)

### Action (Refactor Global End-to-End)

#### 2.1. Persistencia & Migración Flyway V4 (`cocos-trading-service`)
Para que el cambio a `Long` en Java no falle en PostgreSQL al persistir valores > `2,147,483,647`, es obligatorio ampliar la columna física en `orders`:
- Crear **`cocos-trading-service/src/main/resources/db/migration/V4__widen_orders_size_to_bigint.sql`**:
  ```sql
  -- Widen orders.size from INTEGER (int4) to BIGINT (int8) to prevent overflow on high-nominality instruments (e.g., COME, AL30).
  ALTER TABLE orders
      ALTER COLUMN size TYPE BIGINT;
  ```
- **`OrderJpaEntity.java`**:
  - Cambiar `private Integer quantity;` (`@Column(name = "size", nullable = false)`) a `private Long quantity;`.

#### 2.2. Contrato Público (`cocos-trading-contract`)
Actualizar todos los DTOs del contrato para exponer `Long quantity`:
- **`TradeOrderRequestApi.java`**: `Integer quantity` -> `Long quantity` (actualizar Javadoc y `@Schema` si aplica).
- **`TradeOrderApi.java`**: `Integer quantity` -> `Long quantity`.
- **`AssetPositionApi.java`**: `Integer quantity` -> `Long quantity`.

#### 2.3. Capa Web & Validación (`cocos-trading-service`)
- **`ValidatorUtils.java` & `OrderCreateRequestValidator.java`**:
  - Reemplazar o sobrecargar `validatePositiveInteger(...)` por `validatePositiveLong(Errors, Long, String, String, String)` para validar `request.quantity()` sin truncamiento.
- **`ApiMapper.java`**:
  - Verificar mapeos de `tradeRequest.quantity()`, `position.quantity()` y `tradeOrder.getQuantity()` (todos alineados a `Long`).

#### 2.4. Capa de Aplicación & Dominio (`cocos-trading-service`)
- **`PlaceOrderCommand.java`**: `Integer quantity` -> `Long quantity`.
- **`AssetPosition.java`**: `Integer quantity` -> `Long quantity`.
- **`MonetaryAmount.java`**:
  - Actualizar `public MonetaryAmount multiply(int quantity)` -> `public MonetaryAmount multiply(long quantity)` usando `BigDecimal.valueOf(quantity)`.
- **`AccountLedgerCalculator.java`**:
  - `calculateMaxShareQuantity(MonetaryAmount amount, MonetaryAmount unitPrice)`: cambiar retorno de `int` a `long` (usando `.longValue()` en lugar de `.intValue()`, con validación explícita de *overflow* vía `longValueExact()` o guardas seguras si excede `Long.MAX_VALUE`).
  - `calculateAvailableShares(List<TradeOrderJpaEntity> filledTradeOrders, Long instrumentId)`: cambiar acumulador y retorno de `int` a `long` (`long shares = 0L`).
  - Verificar `calculateOrderValue(...)` para asegurar que opere con `long` / `Long` y `BigDecimal.valueOf(long)`.
- **`PortfolioService.java`**:
  - En `PositionAccumulator`: cambiar `private int quantity = 0;` -> `private long quantity = 0L;` y `quantity = 0L;` al cerrar posición.
- **`AbstractEquityOrderExecutionStrategy.java` & `CashTransferExecutionStrategy.java`**:
  - Actualizar `resolvedQuantity`, `resolvedUnits`, `resolveShareQuantity(...)` y `determineStatus(...)` de `int` a `long`.

---

## 3. Objetivo 2: Documentación en `README.md` (SRE & High-Nominality Risks)

### Action
Agregar una nueva subsección breve y directa bajo **`## 3. Decisiones de Diseño / Design Decisions`** en `README.md` (por ejemplo, **`### 3.10. SRE & High-Nominality Risks (Protección contra Integer Overflow & Hiperinflación)`**), cubriendo:

1. **Justificación de `Long` (`BIGINT`) para `quantity` / `size`**:
   - Explicar que `Integer.MAX_VALUE` (`~2.14 * 10^9`) resulta insuficiente para mercados de alta nominalidad como el argentino.
   - Citar explícitamente los casos reales de negocio:
     - **Penny stocks** de alto volumen nominal como **COME** (*Sociedad Comercial del Plata*).
     - **Bonos soberanos** altamente líquidos operados por inversores institucionales o *whales* como **AL30** / **AL29**.
   - En ambos escenarios, una cartera institucional o una orden de gran porte (o movimientos `CASH_IN`/`CASH_OUT` de alto monto mapeados a unidades en `orders.size`) puede superar fácilmente los `2.14` mil millones de nominales, por lo que `Long` (`int8` / `BIGINT`) elimina el riesgo de *Integer Overflow*.
2. **Advertencia Técnica para Esquemas Futuros (`NUMERIC(38, 4)` vs `NUMERIC(19, 2)`)**:
   - Dejar un *warning* técnico explícito recomendando **`NUMERIC(38, 4)`** en lugar de `NUMERIC(19, 2)` (y muy por encima del `NUMERIC(10, 2)` heredado del esquema V1 del challenge) para futuros diseños de base de datos, con el fin de *future-proof* el *ledger* contable frente a agregaciones de volumen en escenarios hiperinflacionarios y preservar precisión sub-centavo en cálculos intermedios.

---

## 4. Objetivo 3: Implementación & Verificación Completa (`mvn clean verify`)

### Action
Una vez aprobado este plan y ejecutado el refactor `Integer -> Long`:

1. **Actualización de Fixtures y Tests**:
   - **`TradingFixtures.java`**: Actualizar firmas de helpers (`filledBuyOrder`, `filledSellOrder`, `marketBuyByQuantity`, etc.) de `int quantity` a `long quantity`.
   - **Unit Tests (`*Test.java`)**: Ajustar `OrderServiceTest.java`, `PortfolioServiceTest.java` (ej. `assertEquals(80L, pampPosition.quantity())`) y `DomainModelTest.java` para compilar y asertar con `Long` (`L`).
   - **Integration Tests (`*IT.java`)**: Verificar `OrderControllerIT.java`, `PortfolioAndInstrumentControllerIT.java` y `PortfolioLifecycleIT.java` (especialmente aserciones `jsonPath("$.quantity")` y `assertEquals` con `Long`), agregando idealmente un caso de borde con nominales `> Integer.MAX_VALUE` (ej. `3_000_000_000L`) para validar protección real de *overflow* end-to-end contra PostgreSQL.
2. **Ejecución de la Suite Completa**:
   - Correr **`mvn clean verify`** (requiere Docker activo para Testcontainers `postgres:16-alpine` y `redis:7-alpine`).
   - Confirmar 0 errores de compilación, 0 *mock mismatches*, 100% de Unit Tests (Surefire) e Integration Tests (Failsafe) en verde, y generación limpia del reporte JaCoCo.

---

## 5. Criterio de Aprobación

Este documento es únicamente el plan de diseño (**Zero Code Changes Applied**). Aguardo tu confirmación para proceder con:
1. Creación de `V4__widen_orders_size_to_bigint.sql` y refactor `Integer -> Long` en contrato, servicio y tests.
2. Actualización de `README.md` con la sección **SRE & High-Nominality Risks**.
3. Ejecución y verificación completa con `mvn clean verify`.

