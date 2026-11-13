# Integration Testing & Dockerization Plan

This document outlines the architecture and implementation plan to upgrade the local development and integration testing environments of the **Cocos Challenge Trading API** to a truly production-ready standard. By eliminating the in-memory H2 database in favor of **Testcontainers (PostgreSQL)**, introducing a multi-container **Docker Compose** stack with automated schema/seed initialization, and formalizing a **Spring Profiles Strategy** (`local`, `test`, `prod`), we guarantee 100% database engine parity across test, local, and production (NeonDB) environments.

---

## Step 1: Testcontainers for Integration Tests (`src/it`)

### 1.1 Motivation & Engine Parity
Currently, functional integration tests (`*IT.java` in `cocos-trading-service/src/it/java`) run against an in-memory H2 database configured in `MODE=PostgreSQL`. While fast, H2 only approximates PostgreSQL syntax and transaction semantics. Replacing H2 with **Testcontainers (`org.testcontainers:postgresql`)** spins up a real, disposable **PostgreSQL 16** container during the Maven `verify` lifecycle (`maven-failsafe-plugin`), ensuring our JPA entities (`users`, `instruments`, `orders`, `marketdata`), native PostgreSQL types, case-sensitivity rules, and Spring Data Specifications execute against the exact same database engine used by NeonDB in production.

### 1.2 Maven Dependency Changes (`pom.xml` & `cocos-trading-service/pom.xml`)
- **Remove**:
  - `com.h2database:h2` (`<scope>test</scope>`) from [cocos-trading-service/pom.xml](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/pom.xml).
- **Add** (versions managed by `spring-boot-starter-parent` `3.3.0`):
  - `org.springframework.boot:spring-boot-testcontainers` (`<scope>test</scope>`) — enables Spring Boot 3.1+ `@ServiceConnection` support, automatically wiring the dynamic container JDBC URL, username, and password into the Spring `DataSource` without boilerplate `@DynamicPropertySource` methods.
  - `org.testcontainers:junit-jupiter` (`<scope>test</scope>`) — JUnit 5 lifecycle integration for Testcontainers.
  - `org.testcontainers:postgresql` (`<scope>test</scope>`) — PostgreSQL Testcontainers module.

### 1.3 Singleton Container Pattern in `TradingApplicationIT.java`
Refactor [TradingApplicationIT.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/it/java/com/cocos/trading/TradingApplicationIT.java) to manage a single shared `PostgreSQLContainer<?>` instance across all `*IT` classes (`OrderControllerIT`, `PortfolioAndInstrumentControllerIT`) within the Failsafe JVM run:
- **Image**: `postgres:16-alpine` (lightweight Alpine image matching NeonDB's PostgreSQL 16 engine).
- **Configuration**:
  - Use `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`, `@AutoConfigureMockMvc`, and `@ActiveProfiles("test")`.
  - Declare a `static final PostgreSQLContainer<?>` annotated with `@ServiceConnection` (or started in a `static` initializer block) so the container starts once per test suite run rather than restarting between test classes, keeping `mvn verify` fast and preventing connection pool exhaustion.
- **Schema Management & Seed Data in Tests**:
  - Under the `test` profile (`src/it/resources/application-test.yml`), Hibernate `ddl-auto: create-drop` will create the tables (`users`, `instruments`, `orders`, `marketdata`) automatically inside the ephemeral Postgres container.
  - Because `create-drop` starts with an empty schema, each `*IT` class (`OrderControllerIT`, `PortfolioAndInstrumentControllerIT`) explicitly inserts the mandatory `MONEDA` (`ARS`) instrument along with test users, `ACCIONES` instruments, `marketdata`, and initial `orders` inside its `@BeforeEach` setup method so `TransferOrder` and `AccountLedgerCalculator` logic always have the required currency instrument present.

---

## Step 2: Docker Compose for Local Development

### 2.1 Multi-Stage `Dockerfile` Upgrade
Upgrade the root [Dockerfile](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/Dockerfile) so `docker compose up --build` works out-of-the-box without requiring a manual host `mvn package` beforehand (or seamlessly supports layered JAR execution):
- **Stage 1 (`builder`)**: `maven:3.9.8-amazoncorretto-21-alpine`
  - Copies `pom.xml`, `cocos-trading-contract/pom.xml`, and `cocos-trading-service/pom.xml` first to cache Maven dependencies (`mvn dependency:go-offline -B`).
  - Copies `src` directories and builds the application (`mvn clean package -DskipTests -B`).
- **Stage 2 (`runtime`)**: `amazoncorretto:21-alpine-jdk`
  - Runs as a non-root user for container security best practices.
  - Copies the built `cocos-trading-service/target/*.jar` to `/app/libs/app.jar`.
  - Exposes port `8080` and runs `ENTRYPOINT ["java", "-jar", "/app/libs/app.jar"]`.

### 2.2 Local PostgreSQL Schema & Seed Script (`docker/postgres/init.sql`)
Create a database initialization script (`docker/postgres/init.sql`) mounted into `/docker-entrypoint-initdb.d/` of the local PostgreSQL container so that on first startup, the local database automatically matches the NeonDB schema and challenge reference dataset (`database.sql`):
- **Tables**:
  - `users` (`id SERIAL PRIMARY KEY`, `email VARCHAR(255)`, `accountnumber VARCHAR(255)`)
  - `instruments` (`id SERIAL PRIMARY KEY`, `ticker VARCHAR(50)`, `name VARCHAR(255)`, `type VARCHAR(50)`)
  - `orders` (`id SERIAL PRIMARY KEY`, `instrumentid BIGINT`, `userid BIGINT`, `size INT`, `price NUMERIC(19, 2)`, `type VARCHAR(50)`, `side VARCHAR(50)`, `status VARCHAR(50)`, `datetime TIMESTAMP`)
  - `marketdata` (`id SERIAL PRIMARY KEY`, `instrumentid BIGINT`, `high NUMERIC(19, 2)`, `low NUMERIC(19, 2)`, `open NUMERIC(19, 2)`, `close NUMERIC(19, 2)`, `previousclose NUMERIC(19, 2)`, `date DATE`)
- **Seed Data**:
  - Pre-populates the reference users (e.g., `emiliano@test.com`, `jose@test.com`, `francisco@test.com`, `juan@test.com`), the `ARS` (`MONEDA`) and equity (`ACCIONES`) instruments (`DYCA`, `CAPX`, `PGR`, `MOLA`, `MIRG`, `PAMP`, `METR`, `ALUA`, `BPAT`, `LOMA`, etc.), initial `marketdata` quotes (`close`, `previousClose`), and historical `orders` (`CASH_IN`, `BUY`, `SELL`).
  - Resets PostgreSQL `SERIAL` sequences (`setval(pg_get_serial_sequence(...))`) after explicit ID inserts so subsequent `POST /orders` requests generate conflict-free primary keys.

### 2.3 Root `docker-compose.yml` Architecture
Upgrade [docker-compose.yml](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/docker-compose.yml) to orchestrate two services on a dedicated bridge network (`cocos-network`):
1. **`cocos-postgres`**:
   - **Image**: `postgres:16-alpine`
   - **Environment**:
     - `POSTGRES_DB: cocos_trading`
     - `POSTGRES_USER: cocos_user`
     - `POSTGRES_PASSWORD: cocos_password`
   - **Ports**: `"5432:5432"` (allowing developers to inspect the local DB via DBeaver/pgAdmin/IntelliJ).
   - **Volumes**:
     - Named volume `postgres_data:/var/lib/postgresql/data` for persistence across container restarts.
     - Bind mount `./docker/postgres/init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro` to initialize the schema and seed data.
   - **Healthcheck**: `pg_isready -U cocos_user -d cocos_trading` with a `5s` interval so the API container only starts after PostgreSQL is ready to accept connections.
2. **`cocos-trading-api`**:
   - **Build**: `.` (root `Dockerfile`).
   - **Depends On**: `cocos-postgres` with `condition: service_healthy`.
   - **Environment**:
     - `SPRING_PROFILES_ACTIVE: local`
     - `DB_HOST: cocos-postgres`
     - `DB_PORT: 5432`
     - `DB_NAME: cocos_trading`
     - `DB_USERNAME: cocos_user`
     - `DB_PASSWORD: cocos_password`
   - **Ports**: `"8080:8080"`

---

## Step 3: Spring Profiles Strategy (`local`, `test`, `prod`)

### 3.1 Base Configuration (`application.yml`)
Refactor [application.yml](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/resources/application.yml) to hold only environment-agnostic settings and default the active profile to `local` for safe developer ergonomics:
- **Shared settings**:
  - `server.port: ${SERVER_PORT:8080}`
  - `spring.application.name: cocos-trading-service`
  - `spring.profiles.default: local` (falls back to `local` when no `SPRING_PROFILES_ACTIVE` is specified).
  - `spring.jpa.open-in-view: false`
  - `spring.jpa.hibernate.naming.physical-strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl`
  - *(Note: `spring.jpa.properties.hibernate.dialect` is intentionally omitted in all profiles as Hibernate 6 / Spring Boot 3.3 automatically infers `PostgreSQLDialect` from the JDBC connection metadata).*
  - `springdoc.swagger-ui.path: /swagger-ui.html` and `springdoc.api-docs.path: /v3/api-docs`

### 3.2 `local` Profile (`application-local.yml`)
Located at `cocos-trading-service/src/main/resources/application-local.yml`:
- **Purpose**: Connects to the local Docker Compose PostgreSQL instance (`localhost:5432` when running from IDE/Maven on the host, or `cocos-postgres:5432` when running inside Docker Compose via environment variable overrides).
- **Datasource Configuration**:
  - `url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:cocos_trading}`
  - `username: ${DB_USERNAME:cocos_user}`
  - `password: ${DB_PASSWORD:cocos_password}`
  - `driver-class-name: org.postgresql.Driver`
- **JPA Configuration**:
  - `hibernate.ddl-auto: validate` (validates that JPA entities match the schema created by `init.sql`).
  - `properties.hibernate.format_sql: true`

### 3.3 `test` Profile (`application-test.yml`)
Located at `cocos-trading-service/src/it/resources/application-test.yml` (replacing the old `application-integration.yml`):
- **Purpose**: Automatically activated via `@ActiveProfiles("test")` in `TradingApplicationIT` during `mvn verify`.
- **Datasource Configuration**:
  - Managed dynamically by Spring Boot's `@ServiceConnection` and the `PostgreSQLContainer` (`postgres:16-alpine`).
- **JPA Configuration**:
  - `hibernate.ddl-auto: create-drop` (automatically provisions the exact schema inside the ephemeral Testcontainers Postgres instance and drops it when the test context closes; `@BeforeEach` in `*IT` classes explicitly seeds the mandatory `MONEDA` instrument and test fixtures).
  - `properties.hibernate.format_sql: true`

### 3.4 `prod` Profile (`application-prod.yml`)
Located at `cocos-trading-service/src/main/resources/application-prod.yml`:
- **Purpose**: Connects to the external cloud **NeonDB** PostgreSQL instance provided in the challenge instructions, while supporting environment variable overrides (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) for secure cloud deployments.
- **Datasource Configuration**:
  - `url: ${DB_URL:jdbc:postgresql://ep-dark-cloud-be97dqg9-pooler.c-9.us-east-2.aws.neon.tech/neondb?sslmode=require&channelBinding=require}`
  - `username: ${DB_USERNAME:neondb_owner}`
  - `password: ${DB_PASSWORD:npg_VGl0rgp7sklR}`
  - `driver-class-name: org.postgresql.Driver`
- **JPA Configuration**:
  - `hibernate.ddl-auto: none` (never mutates the external production schema).
  - `properties.hibernate.format_sql: false`

---

## Step 4: Documentation Updates (`README.md`)

Update [README.md](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/README.md) (preserving the LatAm Fintech Spanglish style established in `001-architecture-refactor.md`) to document:
1. **Tech Stack**: Replace the H2 mention with **Testcontainers (`PostgreSQLContainer`)** and **Docker Compose**.
2. **Perfiles de Ejecución (`Spring Profiles`)**: Clear table and instructions explaining how to run with `local` (Docker Postgres), `test` (Testcontainers via `mvn verify`), and `prod` (NeonDB via `-Dspring-boot.run.profiles=prod` or `SPRING_PROFILES_ACTIVE=prod`).
3. **Levantar el Entorno Completo con Docker Compose**: Commands for `docker compose up --build` (Local Postgres + API) and `docker compose up -d cocos-postgres` (starting only local Postgres while running the API from the IDE/Maven).

