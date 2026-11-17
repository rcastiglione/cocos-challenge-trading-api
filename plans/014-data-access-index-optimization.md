# Plan 014: Data Access Layer & Database Index Alignment (`InstrumentSpecifications`, Standard Ticker B-Tree Index & N+1 Audit)

**Status:** Pending Approval (Design & Planning Only — Zero Code Changes Applied)
**Context:** Cocos Challenge Trading API
**Architecture:** Multi-Module Pragmatic Service-Driven Architecture (Java 25 LTS, Spring Boot 4.x, PostgreSQL 16 + Flyway V3/V4, Redis 7, Hibernate `SINGLE_TABLE`)

---

## 1. Gentle AI & Engram ReAct Loop — Diagnóstico de Alineación JPA vs. Índices B-Tree

### Thought (Análisis del Estado Actual)
En **V3 (`V3__add_performance_indexes.sql`)** introdujimos índices *non-clustered* para los *hot-paths*, incluyendo dos índices sobre `instruments.ticker`:
- `idx_instruments_ticker_lower ON instruments (LOWER(ticker))` (*functional B-Tree index*)
- `idx_instruments_ticker ON instruments (ticker)` (*standard B-Tree index*)
- `idx_marketdata_instrumentid_date_id ON marketdata (instrumentid, date DESC, id DESC)`
- `idx_orders_userid_status_datetime_id ON orders (userid, status, datetime ASC, id ASC)`
- `idx_orders_idempotency_key ON orders (idempotency_key) WHERE idempotency_key IS NOT NULL`

Al auditar la capa de acceso a datos en `cocos-trading-service`, detectamos dos puntos de mejora clave:

1. **Redundancia de Índices en `ticker` & Full Table Scans por *Leading Wildcards* (`%...%`)**:
   - Mantener simultáneamente `idx_instruments_ticker_lower` (`LOWER(ticker)`) e `idx_instruments_ticker` (`ticker`) es **redundante** si garantizamos la invariante de negocio de que todos los `ticker` en base de datos se almacenan en **MAYÚSCULAS** (*canonical uppercase*, ej. `PAMP`, `MOLI`, `ARS`).
   - Además, `InstrumentSpecifications.withFilters(InstrumentSearchQuery)` actualmente construye predicados `cb.like(cb.lower(root.get("ticker")), "%" + ... + "%")` y `cb.like(cb.lower(root.get("name")), "%" + ... + "%")` para `?ticker=` y `?name=`. En PostgreSQL, un índice **B-Tree** no puede optimizar `LIKE '%term%'` con *leading wildcard*, forzando un **Sequential Scan (Full Table Scan)** sobre `instruments`.
2. **Riesgo de Agotamiento de Memoria en Búsquedas Amplias (`?query=`)**:
   - `InstrumentRepositoryAdapter.search(InstrumentSearchQuery)` invoca `springDataInstrumentRepository.findAll(spec, sort)` sin límite de resultados ni paginación. Una búsqueda amplia (ej. `?query=a`) materializa toda la tabla en memoria y dispara enriquecimiento masivo de *market data*.
3. **Auditoría N+1 y `@EntityGraph` en *Hot Paths***:
   - Debemos validar formalmente que `PortfolioService` y los repositorios *core* (`SpringDataOrderRepository`, `SpringDataMarketDataRepository`) estén libres de bucles N+1 y *lazy-loading* accidental.

---

## 2. Objetivo 1: Simplificación de Índice en `ticker`, Exact Index Matching & Safety Limit

### Thought
- **Invariante de `ticker` en Mayúsculas**: Asumimos y enforzamos que todo `ticker` persistido en `instruments` está en **uppercase**. Con esta garantía, el índice estándar `idx_instruments_ticker ON instruments (ticker)` es suficiente y óptimo. Si el query normaliza el parámetro de entrada a `.trim().toUpperCase()`, PostgreSQL resuelve `ticker = 'PAMP'` con un **Index Scan** directo sobre `idx_instruments_ticker`, sin necesidad de `LOWER(ticker)` ni del índice funcional redundante.
- **Distinción `ticker` vs. `name` vs. `query`**:
  - `ticker`: Exact match directo en uppercase (`cb.equal(root.get("ticker"), value.trim().toUpperCase())`), aprovechando `idx_instruments_ticker`.
  - `name`: Exact match *case-insensitive* (`cb.equal(cb.lower(root.get("name")), value.trim().toLowerCase())`), ya que los nombres de compañías contienen mayúsculas y minúsculas mixtas (ej. `"Molinos Río de la Plata"`).
  - `query`: Conserva el `LIKE '%...%'` (*case-insensitive*) sobre `ticker` OR `name` para búsquedas exploratorias / texto parcial, protegido por un límite duro de **50 resultados**.

### Action
1. **Simplificar Índices de `instruments` (Flyway)**:
   - Descartar el índice funcional redundante `idx_instruments_ticker_lower` de `V3__add_performance_indexes.sql` (si V3 aún no fue *mergeado* a la rama principal/producción) **o**, si V3 ya fue aplicado en entornos compartidos, agregar una migración `V5__drop_redundant_ticker_lower_index.sql` con `DROP INDEX IF EXISTS idx_instruments_ticker_lower;`, dejando únicamente **`idx_instruments_ticker`** sobre `instruments (ticker)`.
   - Asegurar (mediante validación/normalización en entidad o capa de servicio/datos si aplica, e invariante documentada en schema) que `instruments.ticker` siempre se almacene en **uppercase**.
   - *(Opcional en `SpringDataInstrumentRepository`)*: Evaluar alinear `findByTickerIgnoreCase(String ticker)` -> `findByTicker(String ticker)` pasando `ticker.trim().toUpperCase()` desde `InstrumentRepositoryAdapter.findByTicker(...)` para que la resolución de divisa/ticker también golpee `idx_instruments_ticker` sin `LOWER()`.

2. **Refactorizar `InstrumentSpecifications.java`**:
   - **`searchQuery.query()` (se mantiene `LIKE` parcial)**:
     - Conservar `cb.like(cb.lower(root.get("ticker")), pattern)` `OR` `cb.like(cb.lower(root.get("name")), pattern)` con `"%" + searchQuery.query().trim().toLowerCase() + "%"` para búsquedas amplias (*autocomplete* / texto libre).
   - **`searchQuery.ticker()` (Exact Match Uppercase directo sobre `ticker`)**:
     - Reemplazar `cb.like(cb.lower(root.get("ticker")), ...)` por igualdad exacta en mayúsculas **sin `cb.lower()`**:
       ```java
       String normalizedTicker = searchQuery.ticker().trim().toUpperCase();
       predicates.add(cb.equal(root.get("ticker"), normalizedTicker));
       ```
     - **Justificación**: Genera SQL `ticker = ?` (ej. `ticker = 'PAMP'`), golpeando directamente el índice B-Tree estándar `idx_instruments_ticker` sin overhead de función `LOWER()`.
   - **`searchQuery.name()` (Exact Match Case-Insensitive)**:
     - Reemplazar `cb.like` con wildcards por igualdad exacta *case-insensitive*:
       ```java
       String normalizedName = searchQuery.name().trim().toLowerCase();
       predicates.add(cb.equal(cb.lower(root.get("name")), normalizedName));
       ```
     - **Justificación**: Los nombres legales de compañías combinan mayúsculas/minúsculas; `cb.equal(cb.lower(...))` garantiza coincidencia exacta sin sensibilidad a capitalización.
   - Actualizar el Javadoc de `InstrumentSpecifications.withFilters(...)` documentando la semántica de cada filtro.

3. **Agregar Límite de Seguridad (`50` registros) en `InstrumentRepositoryAdapter.java`**:
   - En `InstrumentRepositoryAdapter.search(InstrumentSearchQuery query)`, acotar `springDataInstrumentRepository.findAll(spec, ...)` a un máximo de `MAX_SEARCH_RESULTS = 50` (p. ej. `PageRequest.of(0, MAX_SEARCH_RESULTS, sort).getContent()` o `EntityManager` con `setMaxResults(50)`) para prevenir agotamiento de memoria en búsquedas amplias con `?query=` sin paginación expuesta.

---

## 3. Objetivo 2: Auditoría de Prevención de N+1 & `@EntityGraph`

### Thought
Verificar mediante inspección estática de los repositorios y servicios *hot-path* que no existan *lazy-loading* N+1 ni consultas individuales dentro de iteraciones.

### Action (Audit Checklist & Hallazgos Preliminares)
1. **Auditoría de `PortfolioService.getPortfolio(Long userId)`**:
   - **Estado observado**:
     - `PortfolioService` extrae `Set<Long> equityInstrumentIds` de `filledTradeOrders` e invoca **una única consulta batch**: `marketDataRepositoryPort.findLatestByInstrumentIds(equityInstrumentIds)`.
     - `MarketDataRepositoryAdapter.findLatestByInstrumentIds(...)` delega en `SpringDataMarketDataRepository.findAllByInstrumentIdsOrderByDateDesc(instrumentIds)` (`WHERE m.instrumentId IN :instrumentIds ORDER BY m.date DESC, m.id DESC`), aprovechando `idx_marketdata_instrumentid_date_id` y deduplicando en memoria (`putIfAbsent`).
   - **Acción de auditoría**: Confirmar que no quede ninguna llamada a `findLatestByInstrumentId(...)` (singular) dentro de bucles en `PortfolioService` ni en `InstrumentService` (ambos usan `findLatestByInstrumentIds`).
2. **Auditoría de `@EntityGraph` en Repositorios *Hot-Path***:
   - **Estado observado en `SpringDataOrderRepository.java`**:
     - `findById(Long id)` -> `@EntityGraph(attributePaths = {"user", "instrument"})`
     - `findByIdempotencyKey(UUID idempotencyKey)` -> `@EntityGraph(attributePaths = {"user", "instrument"})`
     - `findByUserIdAndStatusOrderByDatetimeAscIdAsc(...)` -> `@EntityGraph(attributePaths = {"user", "instrument"})`
     - `findTradeOrdersByUserIdAndStatus(...)` -> `@EntityGraph(attributePaths = {"user", "instrument"})`
     - `findTransferOrdersByUserIdAndStatus(...)` -> `@EntityGraph(attributePaths = {"user", "instrument"})`
   - **Acción de auditoría**: Verificar que no existan métodos adicionales en los repositorios JPA que accedan a asociaciones `@ManyToOne(fetch = FetchType.LAZY)` en *hot-paths* sin `@EntityGraph`.

---

## 4. Objetivo 3: Actualización de Documentación (`README.md`) & Postman Collection

### Thought
Al cambiar `?ticker=` y `?name=` de *contains* a *exact match* (y simplificar la estrategia de indexación de `ticker` a uppercase estándar), la documentación y la colección de Postman deben reflejar exactamente este contrato y su justificación de performance.

### Action
1. **Actualizar `README.md`**:
   - En la documentación del endpoint **`GET /instruments`**:
     - Especificar claramente que **`?ticker=`** requiere **coincidencia exacta** (ej. `?ticker=PAMP`; la API normaliza la entrada a mayúsculas asumiendo almacenamiento canónico uppercase en DB).
     - Especificar que **`?name=`** requiere **coincidencia exacta *case-insensitive*** (ej. `?name=Molinos Río de la Plata`).
     - Especificar que **`?query=`** debe usarse para **búsquedas parciales (*contains*, *case-insensitive*)** sobre `ticker` o `name` (ej. `?query=pampa`), con un límite de seguridad de **50 resultados**.
   - En la sección de **Decisiones de Diseño / Índices V3**:
     - Actualizar la referencia de índices de `instruments` para documentar que se utiliza únicamente el índice B-Tree estándar **`idx_instruments_ticker`** (`ON instruments (ticker)`), descartando el índice funcional `idx_instruments_ticker_lower` gracias a la invariante de `ticker` en mayúsculas.
2. **Actualizar `cocos-trading-api.postman_collection.json`**:
   - **`Search Instruments by Query`** (`{{baseUrl}}/instruments?query=PAMP`): actualizar su `description` indicando que `?query=` ejecuta búsqueda parcial (*substring match*, *case-insensitive*, máximo 50 resultados).
   - **`Search Instruments by Ticker and Name`** (`{{baseUrl}}/instruments?ticker=MOLI&name=Molinos`): actualmente envía `name=Molinos` (parcial) para `"Molinos Río de la Plata"`, lo cual **fallará** al pasar a `cb.equal`. Actualizar el parámetro a `name=Molinos Río de la Plata` (o `?ticker=MOLI`) y actualizar su `description` dejando explícito que `ticker` (normalizado a uppercase) y `name` (*case-insensitive*) requieren **coincidencia exacta**.

---

## 5. Objetivo 4: Verificación & Ajuste de Tests (`mvn clean verify`)

### Thought
Endurecer `ticker` y `name` a *exact match* provocará fallos en cualquier test que pase subcadenas parciales a dichos parámetros. Debemos ejecutar la suite completa y actualizar/agregar los tests pertinentes.

### Action
1. **Ejecución de la Suite Completa**:
   - Correr **`mvn clean verify`** (con Docker activo para Testcontainers `postgres:16-alpine` y `redis:7-alpine`).
2. **Revisión y Actualización de Tests**:
   - **`PortfolioAndInstrumentControllerIT.java`**:
     - Validar `shouldSearchInstrumentsByTickerAndName()` (usa `query="pampa"` y `ticker="MOLI"`, ambos válidos) y agregar/ajustar casos explícitos para `?ticker=pamp` (normalizado a uppercase -> match exacto con `"PAMP"`), `?ticker=PAM` (sin match), `?name=Molinos Río de la Plata` (match exacto) y `?name=Molinos` (sin match).
   - **`InstrumentSpecificationsTest.java`** (crear/actualizar):
     - Verificar que `ticker` parcial (`"PAM"`) **no** matchea `"PAMP"`, mientras `"pamp"` (normalizado a `"PAMP"`) sí matchea usando igualdad directa.
     - Verificar que `name` parcial (`"Molinos"`) **no** matchea `"Molinos Río de la Plata"`, mientras `"molinos río de la plata"` sí matchea (*case-insensitive*).
     - Verificar que `query` parcial (`"pamp"`) sigue matcheando por `ticker` o `name`.
     - Verificar el tope máximo de 50 resultados en `InstrumentRepositoryAdapter`.
3. **Criterio de Aceptación**:
   - `mvn clean verify` finaliza con **0 fallos** en Surefire y Failsafe, reporte JaCoCo limpio y queries alineadas al índice B-Tree estándar `idx_instruments_ticker`.

---
