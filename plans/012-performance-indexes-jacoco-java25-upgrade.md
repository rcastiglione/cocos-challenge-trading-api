# Plan 012: Database Performance Indexes (Flyway V3), JaCoCo Coverage & Java 25 LTS + Spring Boot 4 Upgrade

**Status:** Pending Approval (Design & Planning Only — Zero Code Changes Applied)
**Context:** Cocos Challenge Trading API
**Architecture:** Multi-Module Pragmatic Service-Driven Architecture (Target: Java 25 LTS, Spring Boot 4.x, PostgreSQL 16 + Flyway V3, Redis 7, Hibernate `SINGLE_TABLE`)

---

## 1. Gentle AI & Engram ReAct Loop — Análisis & Estrategia de Modernización

### Thought (Diagnóstico del Estado Actual)
Al entrar en la fase de modernización y optimización de performance, analizamos el estado actual del repositorio (`pom.xml`, `cocos-trading-service/pom.xml`, `Dockerfile`, `V1__init_schema.sql`, `V2__add_idempotency_key_to_orders.sql`, repositorios JPA y `README.md`):

1. **Database Performance & Indexing (PostgreSQL 16 + Flyway)**:
   - `V1__init_schema.sql` define las tablas `users`, `instruments`, `orders` y `marketdata` únicamente con `PRIMARY KEY`s (`id`). No existen índices secundarios ni compuestos sobre las *foreign keys* (`userid`, `instrumentid`) ni sobre columnas de filtrado/ordenamiento.
   - `V2__add_idempotency_key_to_orders.sql` agrega `idempotency_key UUID` con `CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key)`. En PostgreSQL, toda `UNIQUE` constraint crea implícitamente un **B-Tree Unique Index** con el mismo nombre (`uq_orders_idempotency_key`), aunque puede optimizarse/verificarse explícitamente con un *partial unique index* (`WHERE idempotency_key IS NOT NULL`) o validarse en `pg_indexes`.
   - Al inspeccionar los repositorios Spring Data JPA actuales, los *hot paths* de lectura son:
     - `SpringDataOrderRepository`:
       - `findByUserIdAndStatusOrderByDatetimeAscIdAsc(Long userId, OrderStatus status)`
       - `findTradeOrdersByUserIdAndStatus(Long userId, OrderStatus status)` (`WHERE t.user.id = :userId AND t.status = :status ORDER BY t.datetime ASC, t.id ASC`)
       - `findTransferOrdersByUserIdAndStatus(Long userId, OrderStatus status)` (`WHERE tr.user.id = :userId AND tr.status = :status ORDER BY tr.datetime ASC, tr.id ASC`)
       - `findByIdempotencyKey(UUID idempotencyKey)`
     - `SpringDataMarketDataRepository`:
       - `findFirstByInstrumentIdOrderByDateDescIdDesc(Long instrumentId)`
       - `findAllByInstrumentIdsOrderByDateDesc(Collection<Long> instrumentIds)` (`WHERE m.instrumentId IN :instrumentIds ORDER BY m.date DESC, m.id DESC`)
     - `SpringDataInstrumentRepository` & `InstrumentSpecifications`:
       - `findByTickerIgnoreCase(String ticker)` (genera `LOWER(ticker) = LOWER(?)`)
       - Búsquedas dinámicas en `InstrumentSpecifications` usando `LOWER(ticker)` y `LOWER(name)` con `LIKE`.

2. **JaCoCo Code Coverage Integration**:
   - Actualmente `pom.xml` declara `<jacoco.version>0.8.15</jacoco.version>` y `cocos-trading-service/pom.xml` ya configura `jacoco-maven-plugin` (`prepare-agent` y `report` en fase `verify`) junto con `@{argLine}` en `maven-surefire-plugin` y `maven-failsafe-plugin`.
   - Sin embargo, al migrar a **Java 25 LTS** (bytecode class file version `69`), debemos validar que `jacoco-maven-plugin` soporte Java 25 sin errores de instrumentación (`Unsupported class file major version 69`) y que tanto **Unit Tests (Surefire)** como **Integration Tests (Failsafe)** consoliden su cobertura en el reporte HTML (`cocos-trading-service/target/site/jacoco/index.html`).
   - Además, `README.md` necesita una sección clara y dedicada explicando cómo correr los tests y dónde inspeccionar el reporte HTML de JaCoCo.

3. **Tech Stack Upgrade (Java 25 LTS & Spring Boot 4.x)**:
   - El proyecto corre actualmente sobre **Java 21** y **Spring Boot 3.3.0** (`maven:3.9.9-amazoncorretto-21-alpine` y `amazoncorretto:21-alpine-jdk` en `Dockerfile`).
   - Subir a **Java 25 (LTS)** y **Spring Boot 4.x** implica actualizar `<java.version>`, `spring-boot-starter-parent`, las imágenes base del `Dockerfile` y auditar dependencias críticas de terceros que podrían tener *breaking changes* con Spring Framework 7 / Spring Boot 4 (especialmente `problem-spring-web-starter`, `springdoc-openapi-starter-webmvc-ui`, `lombok` y `jacoco`).

---

## 2. Objetivo 1: Database Performance & Indexing (Flyway V3)

### 2.1. Análisis de Queries & Diseño de Índices Non-Clustered
Se creará la migración **`cocos-trading-service/src/main/resources/db/migration/V3__add_performance_indexes.sql`** utilizando `CREATE INDEX IF NOT EXISTS` para garantizar idempotencia en despliegues:

1. **Composite Index en `orders(userid, status, datetime ASC, id ASC)`**:
   - **Queries beneficiadas:** `findByUserIdAndStatusOrderByDatetimeAscIdAsc`, `findTradeOrdersByUserIdAndStatus` y `findTransferOrdersByUserIdAndStatus`.
   - **Justificación:** Al incluir `(userid, status, datetime ASC, id ASC)` en el B-Tree non-clustered index, PostgreSQL resuelve el filtro `WHERE userid = ? AND status = ?` y el `ORDER BY datetime ASC, id ASC` en un único *Index Scan* ordenado, eliminando operaciones de *Sort* en memoria durante el cálculo de `PortfolioService` y `AccountLedgerCalculator`.

2. **Idempotency Key en `orders(idempotency_key)`**:
   - **Query beneficiada:** `findByIdempotencyKey(UUID idempotencyKey)` en `OrderService.placeOrder`.
   - **Justificación:** `V2__add_idempotency_key_to_orders.sql` ya definió `CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key)`, lo cual genera un índice B-Tree único implícito en PostgreSQL. En `V3` verificaremos/documentaremos explícitamente este índice (o agregaremos un bloque `DO $$ ...` / `CREATE UNIQUE INDEX IF NOT EXISTS` únicamente si el índice subyacente no existiera, evitando duplicar índices idénticos que penalicen los `INSERT`s en `orders`).

3. **Composite Index en `marketdata(instrumentid, date DESC, id DESC)`**:
   - **Queries beneficiadas:** `findFirstByInstrumentIdOrderByDateDescIdDesc` y `findAllByInstrumentIdsOrderByDateDesc`.
   - **Justificación:** Permite resolver el último precio de cierre (`LIMIT 1` o batch `IN (...)`) mediante un *Index Scan* directo en orden descendente sin escanear toda la tabla `marketdata` ni ordenar en memoria.

4. **Indexes en `instruments(ticker)` & Functional Index `instruments(LOWER(ticker))`**:
   - **Query beneficiada:** `findByTickerIgnoreCase(String ticker)` y ordenamientos/búsquedas por `ticker`.
   - **Justificación:** PostgreSQL no utiliza un B-Tree estándar sobre `ticker` cuando JPA genera `WHERE LOWER(ticker) = LOWER(?)`. Un **functional index** `ON instruments (LOWER(ticker))` acelera en `O(log N)` la resolución automática del instrumento `MONEDA` (`"ARS"`) en cada `CASH_IN` / `CASH_OUT` y las búsquedas por ticker. Adicionalmente, un índice sobre `instruments (ticker)` soporta ordenamientos y *lookups* exactos.

### 2.2. DDL Propuesto (`V3__add_performance_indexes.sql`)
```sql
-- =============================================================================
-- Migration V3: Add Non-Clustered Performance Indexes for Hot-Path Queries
-- =============================================================================

-- 1. Orders: Composite index for user portfolio & ledger queries (filter + sort)
CREATE INDEX IF NOT EXISTS idx_orders_userid_status_datetime_id
    ON orders (userid, status, datetime ASC, id ASC);

-- 2. Orders: Ensure explicit unique index on idempotency_key if constraint index is absent
CREATE UNIQUE INDEX IF NOT EXISTS idx_orders_idempotency_key
    ON orders (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- 3. MarketData: Composite index for latest-price lookups (single & batch IN queries)
CREATE INDEX IF NOT EXISTS idx_marketdata_instrumentid_date_id
    ON marketdata (instrumentid, date DESC, id DESC);

-- 4. Instruments: Functional case-insensitive index + standard ticker index
CREATE INDEX IF NOT EXISTS idx_instruments_ticker_lower
    ON instruments (LOWER(ticker));

CREATE INDEX IF NOT EXISTS idx_instruments_ticker
    ON instruments (ticker);
```

---

## 3. Objetivo 2: JaCoCo Code Coverage Integration

### 3.1. Configuración en Maven (`pom.xml` & `cocos-trading-service/pom.xml`)
- **Plugin Management (`pom.xml`):** Actualizar `<jacoco.version>` a la versión más reciente con soporte oficial para **Java 25 (class file version 69)** (o `0.8.15+` / snapshot/release compatible según verificación durante la ejecución).
- **Instrumentación Dual (Surefire + Failsafe):**
  - Mantener `prepare-agent` en `jacoco-maven-plugin` antes de `test` y `integration-test` para que tanto los **Unit Tests** (`*Test.java` vía `maven-surefire-plugin`) como los **Integration Tests** (`*IT.java` vía `maven-failsafe-plugin`) compartan `@{argLine}` y acumulen datos de ejecución en `target/jacoco.exec`.
  - Mantener `report` en la fase `verify` para generar el reporte consolidado HTML/XML/CSV tras la finalización de Surefire y Failsafe.
  - Verificar compatibilidad de `@{argLine} -XX:+EnableDynamicAgentLoading` con Java 25.

### 3.2. Actualización de `README.md` (Sección de Testing & JaCoCo)
Se agregará/actualizará una sección dedicada en `README.md` detallando:
- Prerrequisito de Docker activo para **Testcontainers** (`postgres:16-alpine` + `redis:7-alpine`).
- Comando único para ejecutar Unit Tests + Integration Tests + generación de cobertura:
  ```bash
  mvn clean verify
  ```
- Ruta exacta del reporte HTML generado:
  - **`cocos-trading-service/target/site/jacoco/index.html`** (y comando rápido para abrirlo localmente, ej. `open cocos-trading-service/target/site/jacoco/index.html` en macOS).

---

## 4. Objetivo 3: Tech Stack Upgrade (Java 25 LTS & Spring Boot 4.x)

### 4.1. Actualización de `pom.xml` (Parent & Properties)
- **`spring-boot-starter-parent`:** Migrar de `3.3.0` a **`4.0.x`** (última release estable de la línea `4.0.x`).
- **Java Toolchain Properties:**
  ```xml
  <java.version>25</java.version>
  <maven.compiler.source>25</maven.compiler.source>
  <maven.compiler.target>25</maven.compiler.target>
  ```
- **Auditoría & Bump de Dependencias:**
  - **`lombok.version`:** Actualizar a versión con soporte confirmado para JDK 25 (`1.18.42+` o superior).
  - **`springdoc-openapi-starter-webmvc-ui`:** Actualizar a la línea `3.x` compatible con Spring Boot 4 / Jakarta EE 11.
  - **`problem-spring-web-starter` (Riesgo / Breaking Change):** Evaluar compatibilidad de `org.zalando:problem-spring-web-starter` con Spring Boot 4 / Spring Framework 7 (Jackson 3 y cambios en `WebMvc`). Si no existe versión compatible con Spring Boot 4, migrar `GlobalExceptionHandler` al **`ProblemDetail` nativo de Spring Framework (RFC 7807 / RFC 9457)** manteniendo exactamente el mismo JSON de respuesta (`type`, `title`, `status`, `detail`) para no romper los tests ni el contrato de errores.
  - **Spring Boot 4 Modular Test Starters & Jackson 3:** Validar imports en `*Test.java` y `*IT.java` (`@AutoConfigureMockMvc`, `ObjectMapper` / `JsonMapper` de Jackson, `spring-boot-starter-webmvc-test` si aplica en Boot 4).
  - **`flyway.version`, `testcontainers.version`, `logstash-logback-encoder`:** Alinear con las versiones gestionadas por Spring Boot 4 BOM o sus equivalentes compatibles.

### 4.2. Actualización de `Dockerfile`
Migrar ambos *stages* del *multi-stage build* a **Java 25 LTS** (Amazon Corretto 25 Alpine):
- **Stage 1 (`builder`):** `FROM maven:3.9.9-amazoncorretto-25-alpine AS builder` (o imagen oficial Maven + JDK 25 Alpine disponible en Docker Hub).
- **Stage 2 (`runtime`):** `FROM amazoncorretto:25-alpine-jdk` (actualizando el comentario de la sección a `Lightweight Alpine JDK 25 runtime image`).

### 4.3. Documentación en `README.md` (Arquitectura & Justificación del Upgrade)
Actualizar `README.md` en las secciones `1. Tech Stack` y `2. Arquitectura Multi-Módulo & Hexagonal`:
- Reflejar **Java 25 (LTS)**, **Spring Boot 4.x** y **Flyway `V3__add_performance_indexes.sql`**.
- Incluir una justificación técnica concisa del upgrade:
  - **Java 25 LTS:** Adopción de las últimas optimizaciones de la JVM (incluyendo madurez y estabilidad avanzada de **Virtual Threads** sin *pinning* en bloques `synchronized` — JEP 491, mejoras en Garbage Collectors y *throughput* general).
  - **Spring Boot 4.x:** Mantener el *framework* en el *bleeding edge* sobre Spring Framework 7 y Jakarta EE 11, aprovechando soporte nativo de primera clase para Java 25, observabilidad optimizada y menor *overhead* en tiempo de arranque y ejecución.

---

## 5. Objetivo 4: UTC / ISO 8601 Datetime Standardization

### 5.1. Contexto & Diseño
- Actualmente `ApiMapper.ISO_DATETIME_FORMATTER` formatea `OrderJpaEntity.datetime` usando `"yyyy-MM-dd'T'HH:mm:ss"` sin indicador de zona horaria (ej. `"2026-11-17T18:57:12"`).
- En APIs financieras, todo *timestamp* serializado debe adherir estrictamente al estándar **ISO 8601 en UTC** con el sufijo `Z` (*Zulu time*), ej. `"2026-11-17T18:57:12Z"`.

### 5.2. Cambios Planificados
- **`ApiMapper.java`:** Actualizar `ISO_DATETIME_FORMATTER` a `"yyyy-MM-dd'T'HH:mm:ss'Z'"`.
- **Unit & Integration Tests:** Agregar aserciones explícitas validando el sufijo `Z` en `DomainModelTest` y `OrderControllerIT` (`\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z`).
- **Postman Collection (`cocos-trading-api.postman_collection.json`):** Actualizar todas las respuestas de ejemplo guardadas para incluir el sufijo `Z`.
- **`README.md`:** Documentar la estandarización UTC / ISO 8601 en la sección de Decisiones de Diseño y ejemplos cURL.

---

## 6. Plan de Ejecución y Verificación (Post-Aprobación)
1. Crear `cocos-trading-service/src/main/resources/db/migration/V3__add_performance_indexes.sql` con los índices non-clustered propuestos.
2. Actualizar `pom.xml` (Java 25, Spring Boot 4.x, `lombok`, `springdoc`, `jacoco`, y manejo de `ProblemDetail` si `problem-spring-web-starter` no es compatible) y `cocos-trading-service/pom.xml` si requiere ajustes de plugins/starters.
3. Actualizar `Dockerfile` a las imágenes base de **Java 25 Alpine**.
4. Ajustar cualquier *breaking change* de compilación o test derivado de Spring Boot 4 / Jackson 3.
5. Actualizar `README.md` con la documentación del upgrade a Java 25 + Spring Boot 4, los índices V3 y la guía de ejecución de tests + ubicación del reporte JaCoCo (`cocos-trading-service/target/site/jacoco/index.html`).
6. Ejecutar `mvn clean verify` y validar build 100% green (Unit Tests + Integration Tests con Testcontainers + generación de reporte JaCoCo) y `docker compose build`.

