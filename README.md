# Cocos Capital — Transactional Trading Engine API

Una API RESTful *production-grade* para el **Cocos Capital Backend Challenge**, construida con **Java 21**, **Spring Boot 3.3**, **Lombok** y una **Multi-Module Pragmatic Hexagonal Architecture** con aislamiento estricto de contrato (*Framework-Agnostic Contract*).

---

## 1. Tech Stack

- **Java 21** (*Records*, *Pattern Matching*, *Switch Expressions*)
- **Spring Boot 3.3.0** (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`)
- **Observability & Telemetry Stack** (*Nice-to-Have SRE Layer*): **Micrometer (`micrometer-registry-prometheus`)**, **Distributed Tracing (`micrometer-tracing-bridge-brave` con W3C `traceId`/`spanId` en logs)**, **Prometheus** y **Grafana** (con *Zero-Touch Auto-Provisioning* de *datasource* y *dashboard* en tiempo real)
- **Lombok** (`@SuperBuilder`, `@Builder`, `@Getter`, `@RequiredArgsConstructor`, `@Slf4j`) para reducir *boilerplate* en modelos de dominio, *DTOs*, *JPA Entities* y servicios
- **PostgreSQL 16** (NeonDB cloud PostgreSQL para el perfil `prod`, **Docker Compose** para desarrollo `local`, y **Testcontainers (`PostgreSQLContainer`)** para *functional integration testing* con paridad 100% de motor de base de datos)
- **Zalando Problem (`problem-spring-web-starter`)** para respuestas de error estandarizadas bajo **RFC 7807**
- **SpringDoc OpenAPI 2.5 (Swagger UI)** para documentación interactiva de los *endpoints*
- **Maven (Multi-Module)** con **Surefire** (*unit tests*), **Failsafe** (`src/it` *integration tests* con Testcontainers), y **JaCoCo** (*code coverage*)

---

## 2. Arquitectura Multi-Módulo & Hexagonal

El repositorio está estructurado en dos módulos Maven para desacoplar completamente el contrato público de la API (*API Contract*) de la implementación del motor transaccional (*Core Trading Service*):

```text
cocos-challenge-trading-api/
├── pom.xml                                  # Parent / Aggregator POM
├── Dockerfile                               # Multi-stage Dockerfile (Maven Builder + Corretto 21 Alpine Runtime)
├── docker-compose.yml                       # Orquestación local (PostgreSQL 16 + Trading API + Prometheus + Grafana)
├── docker/
│   ├── postgres/init.sql                    # DDL Schema + Seed Data inicial para PostgreSQL local
│   ├── prometheus/prometheus.yml            # Configuración de scrape cada 5s sobre /actuator/prometheus
│   └── grafana/provisioning/                # Zero-Touch Provisioning de Datasource (Prometheus) y Dashboard JSON
├── cocos-trading-contract/                  # Submódulo 1: Pure Framework-Agnostic API Contract (DTOs, *ContractApi, Enums)
└── cocos-trading-service/                   # Submódulo 2: Core Trading Engine Service
    └── src/
        ├── main/java/com/cocos/trading/
        │   ├── domain/                      # Modelos de dominio puros (Order, TradeOrder, TransferOrder) y excepciones
        │   ├── application/                 # Inbound/Outbound Ports, Use Cases y Order Execution Strategies
        │   ├── controller/                  # REST Controllers, Spring Validators (@InitBinder), ApiMapper & GlobalExceptionHandler
        │   ├── repository/                  # JPA Adapters, JPA Entities, PersistenceMapper & Spring Data Specifications
        │   └── config/                      # Configuración de OpenAPI / Swagger
        ├── main/resources/
        │   ├── application.yml              # Configuración base agnóstica de entorno (default profile: local)
        │   ├── application-local.yml        # Perfil 'local' (Docker PostgreSQL)
        │   └── application-prod.yml         # Perfil 'prod' (NeonDB Cloud PostgreSQL)
        ├── test/java/com/cocos/trading/     # Unit Tests (JUnit 5 + Mockito)
        └── it/
            ├── java/com/cocos/trading/      # Functional Integration Tests (@SpringBootTest + MockMvc + Testcontainers)
            └── resources/
                └── application-test.yml     # Perfil 'test' (Testcontainers PostgreSQL + Hibernate create-drop)
```

---

## 3. Decisiones de Diseño / Design Decisions

### 3.1. Aislamiento del Contrato y Arquitectura Multi-Módulo (`cocos-trading-contract`)
- **Contexto & Motivación**:
  En ecosistemas de microservicios modernos, acoplar los *DTOs* y las interfaces de contrato a un *framework* específico (como `spring-web` o `spring-boot-starter-validation`) obliga a cualquier consumidor futuro del `.jar` a arrastrar dependencias transitivas pesadas o incompatibles con su propio *runtime*.
- **Solución Arquitectónica**:
  El módulo `cocos-trading-contract` fue diseñado como un artefacto `.jar` **100% puro y *framework-agnostic***:
  - **Cero Dependencias de Spring / Jakarta Validation en el Contrato**: Se eliminaron `spring-web` y `spring-boot-starter-validation` del `pom.xml` del contrato. Solo se mantienen herramientas agnósticas de la industria: `lombok` (con `<scope>provided</scope>`, sin *footprint* en *runtime*), `jackson-annotations` (estándar para serialización JSON) y `swagger-annotations-jakarta` (metadatos OpenAPI).
  - **Validaciones Explícitas en el `Controller` (`cocos-trading-service`)**: Los *Request DTOs* no contienen anotaciones de validación acopladas (`@NotNull`, `@Min`). Toda la validación de entrada se ejecuta explícitamente en `cocos-trading-service` mediante clases `Validator` de Spring (`OrderCreateRequestValidator`) registradas vía `@InitBinder` en los *controllers*.
- **Pros & Contras (*Trade-offs*)**:
  - **Pros**:
    1. *Portabilidad Total Inter-Microservicios*: El artefacto `cocos-trading-contract.jar` puede ser consumido directamente por otros microservicios o equipos externos sin importar si utilizan **Spring Boot**, **Quarkus** o **Micronaut**.
    2. *Prevención de Breaking Changes & Tight Coupling*: Evita conflictos de versiones de librerías (*dependency hell*) y garantiza que la evolución interna del servicio no rompa el contrato público.
    3. *Centralización de Reglas de Validación*: Al usar `OrderCreateRequestValidator` con `@InitBinder`, las validaciones complejas *cross-field* (como la exclusión mutua entre `size` y `amount`, o `price` obligatorio cuando `type == LIMIT`) viven en un único componente cohesivo y fácil de testear.
  - **Contras**:
    1. *Mayor Verbosidad en la Capa Web del Servicio*: Las anotaciones de ruteo HTTP (`@RequestMapping`, `@PostMapping`, `@GetMapping`) deben declararse explícitamente en las clases `*Controller` del módulo `cocos-trading-service` en lugar de heredarse de la interfaz.

---

### 3.2. Polimorfismo en el Dominio vs. Unified Ledger en Persistencia (`TradeOrder` vs `TransferOrder`)
- **Contexto & Problema**:
  El esquema SQL provisto en el challenge define una única tabla `orders` que almacena tanto operaciones de mercado sobre activos financieros (`BUY` / `SELL` sobre instrumentos tipo `ACCIONES` con tipos `MARKET` / `LIMIT`) como movimientos de fondeo y retiro de dinero (`CASH_IN` / `CASH_OUT` sobre instrumentos tipo `MONEDA` a precio unitario fijo de `1.00` ARS). Modelar ambos conceptos en una única clase plana dentro del *Domain Layer* acopla reglas de negocio completamente distintas (validación de *buying power* vs tenencia de *shares*, precios de mercado/límite vs paridad `1:1` de `Cash`, o impacto en el cálculo de *Weighted Average Cost*).
- **Solución Arquitectónica**:
  - **Persistencia (*Unified Ledger* en tabla `orders`)**: Se mantiene intacta la tabla única `orders` mapeada por `OrderJpaEntity`. Esto preserva la compatibilidad con el SQL inicial y permite consultar el libro mayor (*Account Ledger*) del usuario en una sola query indexada para calcular su `availableCash` y sus `positions`.
  - **Dominio Desacoplado por Herencia (`Order` -> `TradeOrder` | `TransferOrder`)**: En la capa de dominio definimos la clase base abstracta `Order` y dos subclases concretas:
    - `TradeOrder`: Encapsula exclusivamente operaciones de mercado (`BUY`/`SELL`, `MARKET`/`LIMIT`) sobre `Equity` (`ACCIONES`), validando sus propios invariantes de `side` y calculando su impacto en caja y posiciones.
    - `TransferOrder`: Encapsula movimientos de dinero (`CASH_IN`/`CASH_OUT`) sobre `Cash` (`MONEDA`), asegurando precio unitario `1.00` ARS e invariantes de transferencia.
  - **Discriminador en `PersistenceMapper`**: El componente `PersistenceMapper` actúa como *Discriminator / Anti-Corruption Layer* al traducir `OrderJpaEntity` hacia el dominio inspeccionando el campo `side`: si es una operación de `Cash` (`CASH_IN`/`CASH_OUT`) instancia un `TransferOrder`; si es una operación de mercado (`BUY`/`SELL`) instancia un `TradeOrder`.
- **Pros & Contras (*Trade-offs*)**:
  - **Pros**:
    1. *Separation of Concerns & Rich Domain Model*: Aísla las reglas de negocio de un *trade* bursátil (`TradeOrder`) de una simple transferencia monetaria (`TransferOrder`), permitiendo polimorfismo limpio (ej. `order.getCashImpact()`, `order.cancel()` y filtrado por tipo `instanceof TradeOrder` al valuar el *Portfolio*).
    2. *Performance en Lectura (Unified Ledger)*: Evita `JOIN`s o `UNION`s costosos entre múltiples tablas al reconstruir el *Portfolio* y el saldo disponible (`availableCash`) a partir de las órdenes `FILLED`.
    3. *Zero Schema Migration*: Respeta 100% el modelo relacional provisto por el challenge sin requerir columnas discriminadoras adicionales ni migraciones DDL.
  - **Contras**:
    1. *Responsabilidad de Discriminación en el Mapper*: El `PersistenceMapper` debe discriminar explícitamente el subtipo en base al campo `side` en lugar de delegarlo a estrategias automáticas de herencia JPA (`@Inheritance` / `@DiscriminatorColumn`).
    2. *Semántica Compartida a Nivel Físico*: En la base de datos, la columna `size` representa cantidad de acciones para `TradeOrder` y unidades de moneda (ARS) para `TransferOrder`, diferencia que queda encapsulada y saneada al cruzar hacia el dominio.

---

### 3.3. Ruteo de Órdenes mediante Strategy Pattern (`OrderExecutionStrategy`)
- `MarketOrderExecutionStrategy`: Evalúa órdenes `MARKET` sobre instrumentos de `Equity` (`ACCIONES`) tomando el último precio `MarketData.close` y transiciona órdenes válidas inmediatamente a estado `FILLED`.
- `LimitOrderExecutionStrategy`: Evalúa órdenes `LIMIT` sobre `Equity` (`ACCIONES`) utilizando el `price` límite enviado por el usuario y transiciona órdenes válidas a estado `NEW`.
- `CashTransferExecutionStrategy`: Procesa ingresos (`CASH_IN`) y egresos (`CASH_OUT`) de `Cash` sobre instrumentos `MONEDA` (ARS) a precio unitario `1.00` ARS, generando instancias de `TransferOrder`. Valida estrictamente que un retiro `CASH_OUT` nunca supere el `availableCash` del usuario, arrojando `BusinessRuleViolationException` (traducida a `409 Conflict` / `BusinessRuleViolationProblem` bajo RFC 7807) en caso de fondos insuficientes.

---

### 3.4. Dimensionamiento de Órdenes (`size` vs `amount`), Persistencia de `REJECTED` y Validación de `CASH_OUT`
- **Cálculo de `size` sin Fracciones**: El cliente puede enviar la cantidad exacta de acciones (`size`) o el monto total en ARS a invertir (`amount`). Cuando se envía `amount`, el motor calcula `size = floor(amount / executionPrice)` mediante `BigDecimal.divideToIntegralValue`, garantizando cero fracciones de acción (*fractional shares*) y sin exceder jamás el capital solicitado.
- **Trazabilidad de Órdenes `REJECTED` (`BUY` / `SELL`)**: Si un usuario no posee suficiente `availableCash` para un `BUY`, no posee suficientes acciones (`availableShares < size`) para un `SELL`, o envía un `amount` inferior al precio de 1 acción (`size == 0`), la orden de mercado **se persiste** en la tabla `orders` con estado `REJECTED` para auditoría y se retorna en el *response*.
- **Protección de Retiros (`CASH_OUT`)**: Si un usuario solicita una transferencia saliente (`CASH_OUT`) por un monto superior a su `availableCash`, el dominio rechaza la operación inmediatamente mediante `BusinessRuleViolationException` (`BusinessRuleViolationProblem` HTTP `409 Conflict`) sin persistir el retiro inválido.

---

### 3.5. Valuación del Portfolio & Cálculo de Rendimientos (`WAC`)
- **Available Cash (`availableCash`)**: Derivado dinámicamente del *Unified Ledger* sumando el `getCashImpact()` de todas las órdenes `FILLED` (`+CASH_IN`, `-CASH_OUT`, `-BUY (size * price)`, `+SELL (size * price)`).
- **Total Return (%) mediante *Weighted Average Cost (WAC)***:
  La decisión de utilizar **WAC (Precio Promedio Ponderado de Compra)** para determinar el `averageCost` de cada posición no es arbitraria; responde al estándar de la industria financiera (*financial market standard*) para calcular el *cost basis* real de una posición construida a través de múltiples órdenes `FILLED` ejecutadas a distintos precios a lo largo del tiempo:
  $$\text{Total Return (\%)} = \left(\frac{\text{lastPrice} - \text{averageCost}}{\text{averageCost}}\right) \times 100$$
  > **Ejemplo didáctico**: Si se compran `10` acciones (*shares*) a `$100` y luego `5` acciones a `$150`, el *cost basis* unitario no es ni `$100` ni `$150`, sino el promedio ponderado (`WAC`) de `$116.66` (`(10 * 100 + 5 * 150) / 15`). Esto permite que el cálculo del `Total Return (%)` sea financieramente consistente y matemáticamente exacto ante fluctuaciones de mercado y múltiples entradas/salidas parciales.
- **Daily Return (%)**: Calculado a partir de la última cotización disponible en `MarketData`:
  $$\text{Daily Return (\%)} = \left(\frac{\text{close} - \text{previousClose}}{\text{previousClose}}\right) \times 100$$

---

### 3.6. Defensive Programming (DB Schema vs Domain)
- **Contexto & Detección**: Al inspeccionar los metadatos del *schema* PostgreSQL provisto en **NeonDB**, detectamos que la mayoría de las columnas de negocio (`price`, `size`, `side`, `type`, `status`, `datetime` en `orders`; `ticker`, `name`, `type` en `instruments`; `email`, `accountnumber` en `users`; e `instrumentid`, `close`, `previousclose`, `date` en `marketdata`) están configuradas como `NULLABLE` (`is_nullable = YES`) a nivel DDL, a pesar de ser invariantes obligatorios en el dominio financiero.
- **Decisión Arquitectónica**: Dado que la base de datos remota es compartida y provista por los evaluadores, decidimos **no ejecutar scripts DDL (`ALTER TABLE ... SET NOT NULL`) sobre la infraestructura remota** para evitar alterar el entorno o romper herramientas de evaluación automatizada (*grading suites*). En su lugar, aplicamos **Defensive Programming** reforzando estas restricciones exclusivamente en la capa Java/JPA (`@Column(..., nullable = false)` en `OrderJpaEntity`, `InstrumentJpaEntity`, `UserJpaEntity` y `MarketDataJpaEntity` —dejando `high`, `low` y `open` como *nullable* ya que existen instrumentos sin volumen intradía en el *dataset* real—) junto con invariantes en los constructores de dominio, dejando documentados los `// TODO` correspondientes para una futura migración controlada vía **Flyway / Liquibase**.

---

## 4. Estrategia de Entornos (`Spring Profiles`), Inicialización de Base de Datos & Ejecución

La aplicación implementa tres perfiles de Spring Boot para garantizar aislamiento y paridad total de motor PostgreSQL entre pruebas de integración, desarrollo local y la base en la nube del challenge:

| Perfil | Archivo de Configuración | Base de Datos | Uso / Activación |
| :--- | :--- | :--- | :--- |
| **`local`** *(default)* | `application-local.yml` | **PostgreSQL 16 Local** (`localhost:5432/cocos_trading`) levantado vía `docker-compose.yml` y populado automáticamente con `docker/postgres/init.sql` (`ddl-auto: validate`). | Activo por defecto (`SPRING_PROFILES_ACTIVE=local`) al levantar con Docker Compose o desde Maven/IDE. |
| **`prod`** | `application-prod.yml` | **NeonDB Cloud PostgreSQL** (`ep-dark-cloud-be97dqg9-pooler.c-9.us-east-2.aws.neon.tech:5432/neondb`, `ddl-auto: none`). | Activar mediante `-Dspring-boot.run.profiles=prod` o variable de entorno `SPRING_PROFILES_ACTIVE=prod`. |
| **`test`** | `application-test.yml` | **Testcontainers (`postgres:16-alpine`)** efímero gestionado automáticamente con `@ServiceConnection` (`ddl-auto: create-drop`). | Se activa automáticamente vía `@ActiveProfiles("test")` en `TradingApplicationIT` al correr `mvn clean verify`. |

---

### 4.1. Inicialización Automática de la Base de Datos Local (`docker/postgres/init.sql`)
Cuando se levanta el contenedor `cocos-postgres` por primera vez mediante Docker Compose, la imagen oficial `postgres:16-alpine` ejecuta automáticamente el script montado en `/docker-entrypoint-initdb.d/init.sql` (`docker/postgres/init.sql`).

Este script crea el esquema relacional y popula la base local con un **espejo exacto (1:1) de los datos reales de la base NeonDB del challenge**:
- **`users` (4 registros)**: Usuarios de prueba (`id=1` `emiliano@test.com` al `id=4` `juan@test.com`).
- **`instruments` (66 registros)**: Los 65 activos bursátiles tipo `ACCIONES` (ej. `31=BMA`, `42=MOLI`, `45=LOMA`, `47=PAMP`, `54=METR`, `61=ALUA`) y el instrumento `66=ARS` (`PESOS`, tipo `MONEDA`).
- **`marketdata` (126 registros)**: Cotizaciones históricas reales (`high`, `low`, `open`, `close`, `previousclose`) de las jornadas `2023-07-13` y `2023-07-14`.
- **`orders` (11 registros)**: Libro mayor inicial para `userId=1` con órdenes `CASH_IN`, `CASH_OUT`, `BUY` y `SELL` en estados `FILLED`, `NEW`, `CANCELLED` y `REJECTED`.
- **Sincronización de Secuencias**: Al finalizar los `INSERT`, ejecuta `setval(pg_get_serial_sequence(...))` en las 4 tablas para que las nuevas órdenes creadas vía `POST /orders` continúen con el siguiente `id` correlativo (`id=12`, `13`, etc.) sin colisionar con los datos iniciales.

> **¿Cómo resetear la base local a su estado inicial?**
> Dado que PostgreSQL solo ejecuta `/docker-entrypoint-initdb.d/init.sql` cuando el volumen de datos está vacío, si realizaste pruebas locales y querés volver a popular la base desde cero con los datos originales de `init.sql`, ejecutá:
> ```bash
> docker compose down -v
> docker compose up -d cocos-postgres
> ```

---

### 4.2. Cómo Levantar en Modo `local` (PostgreSQL Local en Docker)

Tenés dos alternativas para correr el entorno `local`:

#### Opción A: Stack Completo en Docker (API + PostgreSQL + Prometheus + Grafana)
Compila el proyecto dentro de un *multi-stage Dockerfile* y levanta todos los contenedores orquestados:
```bash
docker compose up --build
```

#### Opción B: Base PostgreSQL en Docker + API corriendo con Maven en tu máquina
Ideal para desarrollo y debugging local contra el contenedor `cocos-postgres`:
```bash
# 1. Instalar los artefactos del proyecto multi-módulo (cocos-trading-contract) en ~/.m2
mvn clean install -DskipTests

# 2. Levantar únicamente el contenedor de PostgreSQL 16 (ejecuta docker/postgres/init.sql si el volumen es nuevo)
docker compose up -d cocos-postgres

# 3. Iniciar la API con el perfil 'local' (se conecta a localhost:5432/cocos_trading)
mvn -pl cocos-trading-service spring-boot:run -Dspring-boot.run.profiles=local
```

---

### 4.3. Cómo Levantar en Modo `prod` (Conectado a NeonDB Cloud PostgreSQL)
Para ejecutar la API localmente en tu máquina pero conectada directamente a la base de datos remota provista por Cocos en **NeonDB** (`ep-dark-cloud-be97dqg9-pooler.c-9.us-east-2.aws.neon.tech`), utilizá el perfil `prod` (definido en `application-prod.yml`):

> **Requisito previo (Maven Multi-Módulo)**: Al ejecutar `spring-boot:run` sobre el submódulo `-pl cocos-trading-service`, Maven resuelve la dependencia `cocos-trading-contract` desde tu repositorio local (`~/.m2/repository`). Ejecutá primero `mvn clean install -DskipTests` desde la raíz del proyecto.

```bash
# 1. Compilar e instalar el módulo de contrato en ~/.m2 (necesario la primera vez o tras cambios en el contrato)
mvn clean install -DskipTests

# 2. Iniciar la API con el perfil 'prod' conectada a NeonDB
mvn -pl cocos-trading-service spring-boot:run -Dspring-boot.run.profiles=prod
```
*(Opcionalmente, podés sobrescribir las credenciales de conexión mediante las variables de entorno `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`).*

---

### 4.4. Ejecutar Unit Tests & Functional Integration Tests (Testcontainers)
> **Requisito**: Tener **Docker** corriendo localmente para que Testcontainers pueda levantar automáticamente el contenedor efímero `postgres:16-alpine` con el perfil `test`.

```bash
mvn clean verify
```
- **Unit tests** (`*Test.java` en `src/test/java`) se ejecutan vía `maven-surefire-plugin`.
- **Functional integration tests** (`*IT.java` en `src/it/java`) se ejecutan vía `maven-failsafe-plugin` contra un contenedor real de PostgreSQL 16 gestionado por **Testcontainers**.
- **Reporte de cobertura JaCoCo**: Se genera automáticamente en `cocos-trading-service/target/site/jacoco/index.html`.

---

### 4.5. Documentación Interactiva (Swagger UI & Postman)
Una vez iniciada la aplicación (ya sea en `local` o `prod`):
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `http://localhost:8080/v3/api-docs`
- **Postman Collection**: Importar el archivo `cocos-trading-api.postman_collection.json` ubicado en la raíz del repositorio.

---

## 5. Observability, Distributed Tracing & Alerting Stack (*Nice-to-Have SRE Layer*)

Como diferencial de ingeniería (*"Nice to have"*), el proyecto incluye un *stack* completo de **Observabilidad y Telemetría en Tiempo Real** basado en **Spring Boot Actuator**, **Micrometer**, **Micrometer Tracing (Brave)**, **Prometheus** y **Grafana**, pre-configurado en `docker-compose.yml` para monitoreo *out-of-the-box* del procesamiento de órdenes:

### 5.1. Custom Business Metrics (`TradingMetricsService` & `OrderService`)
Además de las métricas estándar de infraestructura (JVM Heap/GC, CPU, HikariCP Connection Pool y HTTP Server Requests), `OrderService` delega en un servicio dedicado de telemetría (`TradingMetricsService`) la instrumentación de métricas financieras con control estricto de cardinalidad (*low-cardinality tags*), manteniendo la lógica de negocio desacoplada del *boilerplate* de Micrometer:
- **`cocos_orders_processed_total` (`Counter`)**:
  Contabiliza el volumen total de órdenes procesadas por el motor transaccional, etiquetadas por:
  - `status`: `FILLED` | `REJECTED` | `NEW` | `CANCELLED`
  - `type`: `MARKET` | `LIMIT`
  - `side`: `BUY` | `SELL` | `CASH_IN` | `CASH_OUT`
- **`cocos_orders_execution_latency_seconds` (`Timer` + Histogram Buckets)**:
  Mide la latencia *end-to-end* de ejecución de órdenes (`OrderService.placeOrder`), permitiendo calcular cuantiles exactos (`p50`, `p95`, `p99`) mediante `histogram_quantile(...)` en Prometheus y Grafana.

### 5.2. Distributed Tracing en Logs (`traceId` & `spanId`)
Mediante **`micrometer-tracing-bridge-brave`** (`100%` *sampling probability* en entorno local y propagación W3C `traceparent`), cada *request* HTTP inyecta automáticamente `traceId` y `spanId` en el contexto MDC de SLF4J/Logback:
```text
INFO [cocos-trading-service,668f4b2e9c1d3a4b5e6f7a8b9c0d1e2f,9c1d3a4b5e6f7a8b] --- [order] Placing order from command=...
INFO [cocos-trading-service,668f4b2e9c1d3a4b5e6f7a8b9c0d1e2f,9c1d3a4b5e6f7a8b] --- [order] Order processed and persisted: id=42, status=FILLED...
```
Esto permite aislar y auditar en milisegundos la traza completa de una orden específica o diagnosticar fallos concurrentes en producción sin necesidad de levantar un contenedor pesado de Zipkin/Jaeger localmente.

### 5.3. Estrategia de Alerting en Producción & Error Budgets (SLOs)
Las métricas customizadas fueron diseñadas específicamente para alimentar sistemas de **Alerting** (**Prometheus Alertmanager** o **Grafana Alerts**) y disparar notificaciones *on-call* (**PagerDuty / Slack**) cuando se consumen los *Error Budgets*:
1. **Anomalía en Tasa de Rechazo de Órdenes (`status="REJECTED"`)**:
   - **PromQL Rule**: `sum(rate(cocos_orders_processed_total{status="REJECTED"}[5m])) / sum(rate(cocos_orders_processed_total[5m])) > 0.15`
   - **Propósito Operativo**: Dispara una alerta a Slack/PagerDuty si el porcentaje de órdenes `REJECTED` supera el `15%` en una ventana móvil de 5 minutos, permitiendo detectar rápidamente desfasajes de *MarketData*, problemas de liquidez en cuentas comitentes o regresiones en clientes *upstream*.
2. **Degradación de Latencia Crítica de Ejecución (`p99` Latency SLO Breach)**:
   - **PromQL Rule**: `histogram_quantile(0.99, sum(rate(cocos_orders_execution_latency_seconds_bucket[5m])) by (le)) > 0.250`
   - **Propósito Operativo**: Dispara un *page* de alta severidad si el percentil `p99` de ejecución de órdenes supera los `250ms`, protegiendo el *latency error budget* del motor transaccional frente a contención de base de datos o agotamiento del pool HikariCP.

### 5.4. Acceso al Stack de Monitoreo (`docker compose up --build`)
- **Actuator Prometheus Scrape Endpoint**: `http://localhost:8080/actuator/prometheus`
- **Actuator Health & Readiness Probes**: `http://localhost:8080/actuator/health`
- **Prometheus UI** (*scrape interval: 5s*): `http://localhost:9090`
- **Grafana UI** (*Zero-Touch Dashboard pre-aprovisionado*): `http://localhost:3000` (Credenciales: `admin` / `admin` -> Dashboard **"Cocos Trading Engine — Real-Time Observability"**)

---

## 6. API Endpoints & Ejemplos cURL

### 1. Consultar el Portfolio de un Usuario (`GET /portfolios/{userId}`)
Retorna el valor total de la cuenta (`totalAccountValue`), los pesos disponibles para operar (`availableCash`) y el listado de posiciones activas (`positions`) con su valuación de mercado y rendimiento.
```bash
curl --location 'http://localhost:8080/portfolios/1'
```

### 2. Buscar Instrumentos del Mercado (`GET /instruments`)
Permite buscar instrumentos por `query` general (coincidencia parcial *case-insensitive* en `ticker` o `name`), o filtrando específicamente por `ticker` y/o `name`:
```bash
# Búsqueda general por query (matchea ticker OR name)
curl --location 'http://localhost:8080/instruments?query=PAMP'

# Filtros específicos por ticker y/o name
curl --location 'http://localhost:8080/instruments?ticker=MOLI&name=Molinos'
```

### 3. Enviar una Orden al Mercado (`POST /orders`)

#### A. Orden `MARKET` de Compra (`BUY`) por Cantidad Exacta de Acciones (`size`)
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

#### B. Orden `MARKET` de Compra (`BUY`) por Monto Total en ARS (`amount`)
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

#### C. Orden `LIMIT` de Compra (`BUY` — Estado `NEW`)
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

#### D. Transferencia Entrante de Dinero (`CASH_IN` sobre `MONEDA`)
```bash
curl --location 'http://localhost:8080/orders' \
--header 'Content-Type: application/json' \
--data '{
    "userId": 1,
    "instrumentId": 66,
    "side": "CASH_IN",
    "type": "MARKET",
    "size": 50000
}'
```

#### E. Transferencia Saliente / Retiro de Dinero (`CASH_OUT` sobre `MONEDA`)
Valida que el monto solicitado no exceda el `availableCash` del usuario (retornando `409 BusinessRuleViolationProblem` en caso de fondos insuficientes):
```bash
curl --location 'http://localhost:8080/orders' \
--header 'Content-Type: application/json' \
--data '{
    "userId": 1,
    "instrumentId": 66,
    "side": "CASH_OUT",
    "type": "MARKET",
    "size": 20000
}'
```

### 4. Cancelar una Orden Pendiente (`POST /orders/{orderId}/cancel`)
Solo las órdenes con estado `NEW` pueden ser transicionadas a `CANCELLED`:
```bash
curl --location --request POST 'http://localhost:8080/orders/5/cancel'
```
