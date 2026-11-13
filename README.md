# Cocos Capital — Transactional Trading Engine API

Una API RESTful *production-grade* para el **Cocos Capital Backend Challenge**, construida con **Java 21**, **Spring Boot 3.3**, **Lombok** y una **Multi-Module Pragmatic Hexagonal Architecture** con aislamiento estricto de contrato (*Framework-Agnostic Contract*).

---

## 1. Tech Stack

- **Java 21** (*Records*, *Pattern Matching*, *Switch Expressions*)
- **Spring Boot 3.3.0** (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`)
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
├── docker-compose.yml                       # Orquestación local (PostgreSQL 16 + Trading API)
├── docker/postgres/init.sql                 # DDL Schema + Seed Data inicial para PostgreSQL local
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
- `CashTransferExecutionStrategy`: Procesa ingresos (`CASH_IN`) y egresos (`CASH_OUT`) de `Cash` sobre instrumentos `MONEDA` (ARS) a precio unitario `1.00` ARS, generando instancias de `TransferOrder`.

---

### 3.4. Dimensionamiento de Órdenes (`size` vs `amount`) y Persistencia de `REJECTED`
- **Cálculo de `size` sin Fracciones**: El cliente puede enviar la cantidad exacta de acciones (`size`) o el monto total en ARS a invertir (`amount`). Cuando se envía `amount`, el motor calcula `size = floor(amount / executionPrice)` mediante `BigDecimal.divideToIntegralValue`, garantizando cero fracciones de acción (*fractional shares*) y sin exceder jamás el capital solicitado.
- **Trazabilidad de Órdenes `REJECTED`**: Si un usuario no posee suficiente `availableCash` para un `BUY` o `CASH_OUT`, no posee suficientes acciones (`availableShares < size`) para un `SELL`, o envía un `amount` inferior al precio de 1 acción (`size == 0`), la orden **se persiste** en la tabla `orders` con estado `REJECTED` para auditoría y se retorna en el *response*.

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

## 4. Estrategia de Entornos (`Spring Profiles`), Docker & Testing

La aplicación implementa tres perfiles de Spring Boot para garantizar aislamiento y paridad total de motor PostgreSQL entre pruebas de integración, desarrollo local y producción:

| Perfil | Archivo de Configuración | Base de Datos | Uso / Activación |
| :--- | :--- | :--- | :--- |
| **`local`** *(default)* | `application-local.yml` | **PostgreSQL 16 Local** (`docker-compose.yml`) con esquema y *seed data* inicializados vía `docker/postgres/init.sql` (`ddl-auto: validate`). | Activo por defecto al levantar con Docker Compose o desde el IDE/Maven sin argumentos adicionales. |
| **`test`** | `application-test.yml` | **Testcontainers (`postgres:16-alpine`)** efímero gestionado automáticamente con `@ServiceConnection` (`ddl-auto: create-drop`). | Se activa automáticamente vía `@ActiveProfiles("test")` en `TradingApplicationIT` al correr `mvn clean verify`. |
| **`prod`** | `application-prod.yml` | **NeonDB Cloud PostgreSQL** (instancia remota provista en el challenge, `ddl-auto: none`). | Activar mediante `SPRING_PROFILES_ACTIVE=prod` o `-Dspring-boot.run.profiles=prod`. |

### 4.1. Ejecutar Unit Tests & Functional Integration Tests (Testcontainers)
> **Requisito**: Tener **Docker** corriendo localmente para que Testcontainers pueda levantar el contenedor efímero `postgres:16-alpine`.

```bash
mvn clean verify
```
- **Unit tests** (`*Test.java` en `src/test/java`) se ejecutan vía `maven-surefire-plugin`.
- **Functional integration tests** (`*IT.java` en `src/it/java`) se ejecutan vía `maven-failsafe-plugin` contra un contenedor real de PostgreSQL 16 gestionado por **Testcontainers**.
- **Reporte de cobertura JaCoCo**: Se genera automáticamente en `cocos-trading-service/target/site/jacoco/index.html`.

### 4.2. Levantar el Entorno Completo con Docker Compose (Perfil `local`)
Para compilar la imagen multi-stage de la API y levantar tanto PostgreSQL 16 (pre-cargado con `docker/postgres/init.sql`) como el servicio en un solo paso:
```bash
docker compose up --build
```

Si preferís correr únicamente la base de datos PostgreSQL en Docker y ejecutar la aplicación desde tu IDE o Maven en tu máquina host (perfil `local` por defecto):
```bash
# 1. Levantar solo el contenedor de PostgreSQL local en background
docker compose up -d cocos-postgres

# 2. Correr la aplicación conectada al Postgres local (perfil 'local' por defecto)
mvn -pl cocos-trading-service spring-boot:run
```

### 4.3. Levantar la Aplicación contra NeonDB (Perfil `prod`)
```bash
mvn -pl cocos-trading-service spring-boot:run -Dspring-boot.run.profiles=prod
```

### 4.4. Documentación Interactiva (Swagger UI & Postman)
Una vez iniciada la aplicación:
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Spec**: `http://localhost:8080/v3/api-docs`
- **Postman Collection**: Importar el archivo `cocos-trading-api.postman_collection.json` ubicado en la raíz del repositorio.

---

## 5. API Endpoints & Ejemplos cURL

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

#### D. Transferencia de Dinero (`CASH_IN` / `CASH_OUT` sobre `MONEDA`)
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

### 4. Cancelar una Orden Pendiente (`POST /orders/{orderId}/cancel`)
Solo las órdenes con estado `NEW` pueden ser transicionadas a `CANCELLED`:
```bash
curl --location --request POST 'http://localhost:8080/orders/5/cancel'
```
