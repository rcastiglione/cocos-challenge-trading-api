# Cocos Capital — Transactional Trading Engine API

Una API RESTful *production-grade* para el **Cocos Capital Backend Challenge**, construida con **Java 21**, **Spring Boot 3.3**, **Lombok** y una **Multi-Module Pragmatic Hexagonal Architecture** con aislamiento estricto de contrato (*Framework-Agnostic Contract*).

---

## 1. Tech Stack

- **Java 21** (*Records*, *Pattern Matching*, *Switch Expressions*, *Virtual Threads*)
- **Spring Boot 3.3.0** (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-data-redis`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`)
- **Redis 7 (`redis:7-alpine`)**: *In-Memory Store & Distributed Lock* para garantizar **Idempotencia** en la creación de órdenes (`Idempotency-Key` con TTL de 24 horas vía comandos atómicos `SET ... NX EX 86400`), previniendo *double-spending* ante *network retries* o ráfagas concurrentes
- **Observability & Telemetry Stack** (*Nice-to-Have SRE Layer*): **Micrometer (`micrometer-registry-prometheus`)**, **Distributed Tracing (`micrometer-tracing-bridge-brave` con W3C `traceId`/`spanId` en logs)**, **Prometheus** y **Grafana** (con *Zero-Touch Auto-Provisioning* de *datasource* y *dashboard* en tiempo real)
- **Lombok** (`@SuperBuilder`, `@Builder`, `@Getter`, `@RequiredArgsConstructor`, `@Slf4j`) para reducir *boilerplate* en modelos de dominio, *DTOs*, *JPA Entities* y servicios
- **PostgreSQL 16** (NeonDB cloud PostgreSQL para el perfil `prod`, **Docker Compose** para desarrollo `local`, y **Testcontainers (`PostgreSQLContainer` + `RedisContainer`)** para *functional integration testing* con paridad 100% de infraestructura)
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
├── docker-compose.yml                       # Orquestación local (PostgreSQL 16 + Redis 7 + Trading API + Prometheus + Grafana)
├── docker/
│   ├── postgres/init.sql                    # DDL Schema + Seed Data inicial para PostgreSQL local
│   ├── prometheus/prometheus.yml            # Configuración de scrape cada 5s sobre /actuator/prometheus
│   └── grafana/provisioning/                # Zero-Touch Provisioning de Datasource (Prometheus) y Dashboard JSON
├── cocos-trading-contract/                  # Submódulo 1: Pure Framework-Agnostic API Contract (DTOs, *ContractApi, Enums)
└── cocos-trading-service/                   # Submódulo 2: Core Trading Engine Service
    └── src/
        ├── main/java/com/cocos/trading/
        │   ├── domain/                      # Value Objects (MonetaryAmount), Read-Models (Portfolio, AssetPosition), Enums y Excepciones
        │   ├── application/                 # Inbound/Outbound Ports, Use Cases, AccountLedgerCalculator y Execution Strategies
        │   ├── controller/                  # REST Controllers, Spring Validators (@InitBinder), ApiMapper & GlobalExceptionHandler
        │   ├── repository/                  # JPA & Redis Adapters, JPA Entities (@ManyToOne) y Spring Data Repositories (@EntityGraph)
        │   └── config/                      # Configuración de OpenAPI / Swagger
        ├── main/resources/
        │   ├── application.yml              # Configuración base agnóstica de entorno (default profile: local)
        │   ├── application-local.yml        # Perfil 'local' (Docker PostgreSQL + Redis)
        │   └── application-prod.yml         # Perfil 'prod' (NeonDB Cloud PostgreSQL + Redis)
        ├── test/java/com/cocos/trading/     # Unit Tests (JUnit 5 + Mockito)
        └── it/
            ├── java/com/cocos/trading/      # Functional Integration Tests (@SpringBootTest + MockMvc + PostgreSQL/Redis Testcontainers)
            └── resources/
                └── application-test.yml     # Perfil 'test' (Testcontainers PostgreSQL + Redis + Hibernate create-drop)
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
    3. *Centralización de Reglas de Validación*: Al usar `OrderCreateRequestValidator` con `@InitBinder`, las validaciones complejas *cross-field* (como la exclusión mutua entre `quantity` y `amount`, o `price` obligatorio cuando `type == LIMIT`) viven en un único componente cohesivo y fácil de testear.
  - **Contras**:
    1. *Mayor Verbosidad en la Capa Web del Servicio*: Las anotaciones de ruteo HTTP (`@RequestMapping`, `@PostMapping`, `@GetMapping`) deben declararse explícitamente en las clases `*Controller` del módulo `cocos-trading-service` en lugar de heredarse de la interfaz.

---

### 3.2. Arquitectura Pragmática Data-Centric con Herencia `SINGLE_TABLE` en JPA (`OrderJpaEntity` -> `TradeOrderJpaEntity` | `TransferOrderJpaEntity`)
- **Contexto & Problema**:
  El esquema SQL provisto en el challenge define una única tabla `orders` que almacena tanto operaciones de mercado sobre activos financieros (`BUY` / `SELL` sobre instrumentos tipo `ACCIONES`) como movimientos de efectivo (`CASH_IN` / `CASH_OUT` sobre instrumentos tipo `MONEDA`). Cuando se adopta un **Anemic Domain Model** donde toda la lógica de negocio vive en los Servicios y Estrategias, mantener dos jerarquías paralelas (clases de dominio anémicas vs. entidades JPA anémicas) junto con un `PersistenceMapper` intermedio genera *boilerplate* y complejidad accidental.
- **Solución Arquitectónica**:
  - **Herencia Polimórfica en JPA (`SINGLE_TABLE` + `@DiscriminatorFormula`) sobre un Modelo Anémico Unificado**:
    - Eliminamos las clases espejo redundantes y `PersistenceMapper`, modelando la jerarquía polimórfica directamente en JPA mediante `public abstract class OrderJpaEntity` (`@Inheritance(strategy = InheritanceType.SINGLE_TABLE)`) y sus dos subclases concretas:
      - `TradeOrderJpaEntity` (`@DiscriminatorValue("TRADE")`): Operaciones de mercado (`BUY`, `SELL`) sobre `ACCIONES`.
      - `TransferOrderJpaEntity` (`@DiscriminatorValue("TRANSFER")`): Movimientos de efectivo (`CASH_IN`, `CASH_OUT`) sobre `MONEDA`.
    - **Discriminación sin Mutar el Esquema SQL (`@DiscriminatorFormula`)**: Dado que no podemos alterar la tabla `orders` en NeonDB para agregar una columna `dtype`, utilizamos `@DiscriminatorFormula("CASE WHEN side IN ('CASH_IN', 'CASH_OUT') THEN 'TRANSFER' ELSE 'TRADE' END")` de Hibernate. Esto evalúa el discriminador dinámicamente sobre la columna `side` existente al hacer `SELECT` sin interferir con los `INSERT`/`UPDATE`.
    - **Asociaciones `@ManyToOne` y `@EntityGraph`**: `OrderJpaEntity` mapea `user` (`UserJpaEntity`) e `instrument` (`InstrumentJpaEntity`) mediante `@ManyToOne(fetch = FetchType.LAZY, optional = false)` y `@JoinColumn(name = "userid" / "instrumentid")`, hidratados en un único `JOIN` SQL vía `@EntityGraph(attributePaths = {"user", "instrument"})` en `SpringDataOrderRepository`.
    - **Centralización Total de Comportamiento en Servicios**: Todos los cálculos contables y de dimensionamiento viven en `AccountLedgerCalculator` utilizando *Pattern Matching for `switch`* de Java 21 sobre `TradeOrderJpaEntity` y `TransferOrderJpaEntity`, mientras que `PortfolioService`, `OrderService` y `OrderExecutionStrategy` orquestan el negocio y `ApiMapper` traduce directamente hacia el contrato público.
- **Pros & Contras (*Trade-offs*)**:
  - **Pros**:
    1. *Polimorfismo de Tipos sin Duplicación de Clases*: Combina la expresividad de `TradeOrderJpaEntity` y `TransferOrderJpaEntity` (permitiendo *Pattern Matching* e `instanceof` seguros) con la simplicidad de una única jerarquía de entidades persistentes sin `PersistenceMapper`.
    2. *Grafo de Objetos Completo sin N+1 Queries*: Gracias a `@ManyToOne` + `@EntityGraph` en `SpringDataOrderRepository`, al recuperar las órdenes de un usuario se obtienen simultáneamente sus entidades `UserJpaEntity` e `InstrumentJpaEntity` en un único `JOIN` SQL.
    3. *Zero Schema Migration*: Respeta 100% el modelo relacional fijo provisto por el challenge (las columnas físicas en PostgreSQL siguen siendo `userid`, `instrumentid` y `side`, sin requerir una columna `dtype`).
  - **Contras**:
    1. *Uso de Anotación Específica de Hibernate (`@DiscriminatorFormula`)*: Acopla la resolución del discriminador al proveedor ORM Hibernate (estándar por defecto en Spring Data JPA) para evitar agregar una columna discriminadora física en la base de datos del evaluador.

---

### 3.3. Ruteo de Órdenes mediante Strategy Pattern (`OrderExecutionStrategy`)
- `MarketOrderExecutionStrategy`: Evalúa órdenes `MARKET` sobre instrumentos de `Equity` (`ACCIONES`) tomando el último precio `MarketData.close` y transiciona órdenes válidas inmediatamente a estado `FILLED`.
- `LimitOrderExecutionStrategy`: Evalúa órdenes `LIMIT` sobre `Equity` (`ACCIONES`) utilizando el `price` límite enviado por el usuario y transiciona órdenes válidas a estado `NEW`.
- `CashTransferExecutionStrategy`: Procesa ingresos (`CASH_IN`) y egresos (`CASH_OUT`) de `Cash` sobre instrumentos `MONEDA` (ARS) a precio unitario `1.00` ARS, generando instancias de `TransferOrder`. Valida estrictamente que un retiro `CASH_OUT` nunca supere el `availableCash` del usuario, arrojando `BusinessRuleViolationException` (traducida a `409 Conflict` / `BusinessRuleViolationProblem` bajo RFC 7807) en caso de fondos insuficientes.

---

### 3.4. Polimorfismo en el Contrato API (`OrderCreateRequestApi` & `OrderApi` con `@JsonTypeInfo` / `@JsonSubTypes`) para Separar Trade Orders (`quantity` & `price`) de Transfer Orders (`amount`)
- **Eliminación del Anti-Patrón "Fat DTO" mediante `sealed interface` y Discriminación por `side`**:
  En lugar de exponer un único DTO monolítico lleno de campos opcionales ocultos con `@JsonInclude(NON_NULL)`, modelamos el contrato público (`cocos-trading-contract`) con **Polimorfismo de Tipos en Java 21 (`sealed interface`) y Jackson (`@JsonTypeInfo(use = Id.NAME, include = As.EXISTING_PROPERTY, property = "side")`)**, reflejando 1:1 la jerarquía polimórfica de persistencia (`OrderJpaEntity` $\rightarrow$ `TradeOrderJpaEntity` | `TransferOrderJpaEntity`):
  - **Trade Orders (`BUY` / `SELL` sobre `ACCIONES` $\rightarrow$ `TradeOrderRequestApi` & `TradeOrderApi`)**:
    - En el *request* (`TradeOrderRequestApi`), el cliente puede enviar la cantidad exacta de acciones (`quantity`) o el monto total en ARS a invertir (`amount`), más el `price` límite cuando `type == LIMIT`. Cuando se envía `amount`, el motor calcula `quantity = floor(amount / executionPrice)` mediante `BigDecimal.divideToIntegralValue`, garantizando cero fracciones de acción (*fractional shares*) sin exceder jamás el capital solicitado.
    - En el *response* (`TradeOrderApi`), el contrato expone estructuralmente **solo** la `quantity` entera de acciones ejecutada y el `price` unitario (`MonetaryAmount`), sin incluir el campo `amount`.
  - **Transfer Orders (`CASH_IN` / `CASH_OUT` sobre `MONEDA` $\rightarrow$ `TransferOrderRequestApi` & `TransferOrderApi`)**:
    - Representan flujos directos de efectivo fiduciario, no una orden de ejecución bursátil ni una compra de acciones a precio `$1.00`. Por ello, `TransferOrderRequestApi` requiere únicamente `userId`, `side` y `amount` (`MonetaryAmount`), eliminando por completo `instrumentId`, `type` (`MARKET`/`LIMIT`), `quantity` y `price` (y rechazando con `400 Bad Request` cualquier propiedad ajena mediante `@JsonIgnoreProperties(ignoreUnknown = false)` o cualquier moneda distinta de `"ARS"`).
    - **Resolución Interna de Instrumento por Divisa (`amount.currency`) y Simetría Bidireccional (`Request -> Response`)**: El cliente no necesita conocer el ID interno de la tabla `instruments` para depositar o retirar pesos; `OrderService` resuelve automáticamente el instrumento `MONEDA` buscando por `ticker == amount.currency()` (`"ARS"` $\rightarrow$ `id = 66`), mientras que `ApiMapper` inyecta internamente `OrderType.MARKET` y `CashTransferExecutionStrategy` traduce el monto hacia las columnas físicas `instrumentid = 66`, `size`, `price = 1.00`, `type = 'MARKET'` y `status = 'FILLED'` de la tabla `orders` (**Zero DDL Schema Mutation**). Por consistencia bidireccional, dado que no se solicita `instrumentId` en la entrada, la respuesta (`TransferOrderApi`) retorna únicamente `id`, `user`, `amount`, `side`, `status` (siempre `"FILLED"` por ejecución inmediata) y `datetime`, omitiendo `instrument`, `type`, `quantity` y `price`.
  - **Exhaustive Pattern Matching (`switch`)**: Tanto `OrderCreateRequestValidator` como `ApiMapper` utilizan *Pattern Matching for `switch`* exhaustivo de Java 21 sobre las `sealed interface`s (`OrderCreateRequestApi` y `OrderApi`), garantizando seguridad de tipos en tiempo de compilación sin condicionales complejos.
- **Trazabilidad de Órdenes `REJECTED` (`BUY` / `SELL`)**: Si un usuario no posee suficiente `availableCash` para un `BUY`, no posee suficientes acciones (`availableShares < quantity`) para un `SELL`, o envía un `amount` inferior al precio de 1 acción (`quantity == 0`), la orden de mercado **se persiste** en la tabla `orders` con estado `REJECTED` para auditoría y se retorna en el *response*.
- **Protección de Retiros (`CASH_OUT`)**: Si un usuario solicita una transferencia saliente (`CASH_OUT`) por un `amount` superior a su `availableCash`, el dominio rechaza la operación inmediatamente mediante `BusinessRuleViolationException` (`BusinessRuleViolationProblem` HTTP `409 Conflict`) sin persistir el retiro inválido.

---

### 3.5. Valuación del Portfolio & Cálculo de Rendimientos (`WAC`)
- **Available Cash (`availableCash`)**: Derivado dinámicamente del *Unified Ledger* sumando el `calculateCashImpact()` de todas las órdenes `FILLED` (`+CASH_IN`, `-CASH_OUT`, `-BUY (quantity * price)`, `+SELL (quantity * price)`).
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

### 3.7. Idempotencia y Control de Concurrencia Distribuida (`Idempotency-Key` + Redis)
- **Contexto & Problema (*Double-Spending & Retry Storms*)**:
  En motores de trading y pasarelas de pago (*payment gateways*), los clientes móviles o *frontends* web frecuentemente sufren *timeouts* de red o micro-cortes tras enviar un `POST /orders` y reintentan automáticamente la petición (*network retries*), o bien un usuario ansioso hace *double-click* disparando dos *requests* simultáneos. Sin un mecanismo de **Idempotencia y Distributed Locking**, un reintento sobre un `CASH_OUT`, un `CASH_IN` o una orden `MARKET BUY`/`SELL` provocaría **duplicación de órdenes y *double-spending*** sobre el saldo comitente del usuario.
- **Solución Arquitectónica (`Idempotency-Key` + Redis `SET NX EX`)**:
  1. **Contrato Explícito (`Idempotency-Key` UUID Header)**: El endpoint `POST /orders` exige un *header* HTTP obligatorio `Idempotency-Key` en formato `UUID` (documentado en `OrderContractApi` / OpenAPI y validado en el *controller*). Si el *header* falta o no es un UUID válido, la API responde inmediatamente con `400 Bad Request` (`BadRequestProblem` bajo RFC 7807).
  2. **Distributed Lock Atómico & Deduplicación con TTL de 24h (`RedisIdempotencyAdapter`)**:
     A través del puerto de salida `IdempotencyRepositoryPort` (preservando la arquitectura hexagonal pura), `OrderService.placeOrder` invoca `tryAcquire(idempotencyKey, Duration.ofHours(24))`, el cual ejecuta en **Redis** el comando atómico:
     ```text
     SET idempotency:orders:<Idempotency-Key> <timestamp> NX EX 86400
     ```
     - **`NX` (*Not Exists*)**: Garantiza a nivel de *single-threaded event loop* en Redis que, si llegan 10 *requests* concurrentes en el mismo milisegundo con la misma `Idempotency-Key`, **exactamente uno** adquiere el *lock* e ingresa a la transacción de negocio (`placeOrder`), mientras que los 9 restantes reciben `false` y son rechazados instantáneamente con `409 Conflict` (`DuplicateOrderException` -> `BusinessRuleViolationProblem`) sin siquiera consumir conexiones de PostgreSQL.
     - **`EX 86400` (*24-Hour TTL*)**: Una vez que la orden se procesa y persiste exitosamente (`FILLED`, `NEW` o `REJECTED`), la clave permanece registrada en Redis durante **24 horas**, bloqueando cualquier reintento tardío de red dentro de esa ventana.
     - **Compensación ante Rollback (`release`)**: Si ocurre una excepción antes de persistir la orden (por ejemplo, un `CASH_OUT` rechazado por fondos insuficientes o un `ResourceNotFoundException` que hace *rollback* de la transacción), el bloque `catch` libera la clave en Redis (`DEL`) para no bloquear innecesariamente la clave.
- **Decisión Arquitectónica: ¿Por qué Redis en lugar de `UNIQUE(idempotency_key)` en PostgreSQL?**:
  En un entorno productivo real, además del *fast-path lock* en Redis, agregaríamos una columna `idempotency_key UUID NOT NULL` con un *constraint* `UNIQUE(idempotency_key)` directamente en la tabla `orders` de PostgreSQL mediante una migración de **Flyway / Liquibase** como red de seguridad ACID definitiva (dejamos documentado este `// TODO` explícitamente en `OrderJpaEntity` y `OrderService`). Sin embargo, para **respetar estrictamente el esquema de base de datos fijo provisto por el evaluador del challenge** en NeonDB sin ejecutar `ALTER TABLE` destructivos, implementamos la capa de idempotencia 100% desacoplada en **Redis**.

---

### 3.8. Money Pattern (`MonetaryAmount`) y Agrupamiento Semántico de Entidades (`user` & `instrument`)
- **Contexto & Problemas (*Scalar Money Anti-Pattern, Primitive Obsession & Flat Payloads*)**:
  1. **En el Contrato HTTP**: Representar importes monetarios (`totalAccountValue`, `availableCash`, `marketValue`, `lastPrice`, `price`, `amount`) como números escalares planos (`BigDecimal`) en una API Fintech es un *anti-pattern* que introduce ambigüedad de divisa y riesgo de *currency mismatch* cuando la plataforma evoluciona hacia operatoria bimonetaria o multimoneda (`ARS`, `USD` MEP, `USD` CCL).
  2. **En el Dominio (*Primitive Obsession*)**: Manipular `BigDecimal` sueltos en `Order`, `Portfolio` y `AssetPosition` dispersa las reglas de escala (`scale = 2`, `RoundingMode.HALF_UP`) y carece de control de divisa en sumas o comparaciones.
  3. **Cohesión y Simetría del Payload JSON**: Exponer `userId` y `accountNumber` sueltos en la raíz del Portfolio, mezclar `instrumentId`, `ticker` y `name` al mismo nivel que métricas dinámicas en cada posición, o retornar `userId` e `instrumentId` planos en las respuestas de órdenes (`POST /orders`), rompe la simetría del contrato y degrada la semántica REST.
- **Solución Arquitectónica (Tactical DDD & API Design)**:
  - **Money Pattern End-to-End (`MonetaryAmount`)**:
    - En `cocos-trading-contract`, todos los importes y precios se transportan mediante el objeto compuesto `MonetaryAmount(BigDecimal amount, String currency)` (`{ "amount": 925.85, "currency": "ARS" }`), validado explícitamente en `OrderCreateRequestValidator`.
    - En `cocos-trading-service`, el *Value Object* de dominio `MonetaryAmount` encapsula la normalización de escala (`2` decimales `HALF_UP`) y toda la aritmética financiera segura (*currency-safe arithmetic*: `add`, `subtract`, `multiply`, `negate`, `divideToIntegralShares`, `isLessThan`), verificando `assertSameCurrency` antes de cada operación.
  - **Agrupamiento Semántico de Entidades, Contrato Polimórfico (`TradeOrderApi` vs. `TransferOrderApi`) y Simetría Estricta (`PortfolioApi` & `OrderApi`)**:
    - Se impuso **simetría estricta en toda la superficie de la API** reutilizando los mismos *Summary DTOs* (`UserSummaryApi` para `"user": { "id", "accountNumber" }` e `InstrumentSummaryApi` para `"instrument": { "id", "ticker" }` en operaciones bursátiles). Asimismo, mediante las `sealed interface`s `OrderCreateRequestApi` y `OrderApi` discriminadas por `"side"` y *Pattern Matching* en `ApiMapper`, las órdenes de renta variable (`TradeOrderJpaEntity` $\rightarrow$ `TradeOrderApi`) exponen `instrument`, `quantity`, `price` y `type`, mientras que las transferencias monetarias (`TransferOrderJpaEntity` $\rightarrow$ `TransferOrderApi`) exponen únicamente `id`, `user`, `amount`, `side`, `status` (`FILLED`) y `datetime` (sin `instrument`, `type`, `quantity` ni `price`).
    - En `OrderJpaEntity`, el atributo `private Integer quantity` se mapea explícitamente con `@Column(name = "size", nullable = false)` para unificar el lenguaje ubicuo en Java y en el JSON sin alterar la columna física `size` de la tabla `orders`.
    - Al asociar `OrderJpaEntity` directamente con `UserJpaEntity` e `InstrumentJpaEntity` (hidratadas en memoria durante `OrderService.placeOrder` o vía `@ManyToOne` + `@EntityGraph` en `SpringDataOrderRepository`), `ApiMapper.toApi(OrderJpaEntity)` construye los resúmenes anidados con **cero overhead de I/O adicional** hacia PostgreSQL, elevando la **Developer Experience (DX)** para consumidores frontend y móviles.
  - **Preservación del Esquema SQL**: Siguiendo la regla del challenge (*"Los precios de los activos tienen que estar en pesos"*), las entidades JPA persisten los importes escalares en las columnas `NUMERIC` originales mientras los servicios y `ApiMapper` operan y exponen `MonetaryAmount`, manteniendo **100% intacto el esquema de base de datos original del evaluador**.

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

#### Opción B: Base PostgreSQL + Redis en Docker + API corriendo con Maven en tu máquina
Ideal para desarrollo y debugging local contra los contenedores `cocos-postgres` y `cocos-redis`:
```bash
# 1. Instalar los artefactos del proyecto multi-módulo (cocos-trading-contract) en ~/.m2
mvn clean install -DskipTests

# 2. Levantar los contenedores de PostgreSQL 16 y Redis 7
docker compose up -d cocos-postgres cocos-redis

# 3. Iniciar la API con el perfil 'local' (se conecta a localhost:5432/cocos_trading y Redis localhost:6379)
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
Retorna los datos del titular agrupados bajo `user`, el valor total de la cuenta (`totalAccountValue`), los pesos disponibles para operar (`availableCash`) como objetos `MonetaryAmount`, y el listado de posiciones activas (`positions`) con la identidad del activo agrupada bajo `instrument`:
```bash
curl --location 'http://localhost:8080/portfolios/1'
```
Ejemplo de respuesta (`200 OK`):
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

### 2. Buscar Instrumentos del Mercado (`GET /instruments`)
Permite buscar instrumentos por `query` general (coincidencia parcial *case-insensitive* en `ticker` o `name`), o filtrando específicamente por `ticker` y/o `name`:
```bash
# Búsqueda general por query (matchea ticker OR name)
curl --location 'http://localhost:8080/instruments?query=PAMP'

# Filtros específicos por ticker y/o name
curl --location 'http://localhost:8080/instruments?ticker=MOLI&name=Molinos'
```

### 3. Enviar una Orden al Mercado (`POST /orders`)
> **Nota de Idempotencia**: Todos los *requests* a `POST /orders` requieren el *header* obligatorio `Idempotency-Key` con un `UUID` único por operación (almacenado en Redis con un TTL de 24 horas). Enviar dos veces el mismo `Idempotency-Key` retorna `409 Conflict` (`Duplicate Order`).

#### A. Orden `MARKET` de Compra (`BUY`) por Cantidad Exacta de Acciones (`quantity`)
```bash
curl --location 'http://localhost:8080/orders' \
--header 'Idempotency-Key: 550e8400-e29b-41d4-a716-446655440001' \
--header 'Content-Type: application/json' \
--data '{
    "userId": 1,
    "instrumentId": 47,
    "side": "BUY",
    "type": "MARKET",
    "quantity": 10
}'
```
Ejemplo de respuesta (`201 Created`):
```json
{
  "id": 12,
  "user": {
    "id": 1,
    "accountNumber": "10001"
  },
  "instrument": {
    "id": 47,
    "ticker": "PAMP"
  },
  "quantity": 10,
  "price": {
    "amount": 925.85,
    "currency": "ARS"
  },
  "type": "MARKET",
  "side": "BUY",
  "status": "FILLED",
  "datetime": "2024-06-15T14:30:00"
}
```

#### B. Orden `MARKET` de Compra (`BUY`) por Monto Total en ARS (`amount` como `MonetaryAmount`)
```bash
curl --location 'http://localhost:8080/orders' \
--header 'Idempotency-Key: 550e8400-e29b-41d4-a716-446655440002' \
--header 'Content-Type: application/json' \
--data '{
    "userId": 1,
    "instrumentId": 47,
    "side": "BUY",
    "type": "MARKET",
    "amount": {
        "amount": 15000.00,
        "currency": "ARS"
    }
}'
```
Ejemplo de respuesta (`201 Created` — retorna `quantity` de acciones calculada y `price` de ejecución, omitiendo `amount`):
```json
{
  "id": 13,
  "user": {
    "id": 1,
    "accountNumber": "10001"
  },
  "instrument": {
    "id": 47,
    "ticker": "PAMP"
  },
  "quantity": 16,
  "price": {
    "amount": 925.85,
    "currency": "ARS"
  },
  "type": "MARKET",
  "side": "BUY",
  "status": "FILLED",
  "datetime": "2024-06-15T14:31:00"
}
```

#### C. Orden `LIMIT` de Compra (`BUY` — Estado `NEW`, `price` como `MonetaryAmount`)
```bash
curl --location 'http://localhost:8080/orders' \
--header 'Idempotency-Key: 550e8400-e29b-41d4-a716-446655440003' \
--header 'Content-Type: application/json' \
--data '{
    "userId": 1,
    "instrumentId": 47,
    "side": "BUY",
    "type": "LIMIT",
    "quantity": 5,
    "price": {
        "amount": 900.00,
        "currency": "ARS"
    }
}'
```

#### D. Transferencia Entrante de Dinero (`CASH_IN` usando `amount` — resuelve el instrumento `ARS` automáticamente desde `amount.currency`)
```bash
curl --location 'http://localhost:8080/orders' \
--header 'Idempotency-Key: 550e8400-e29b-41d4-a716-446655440004' \
--header 'Content-Type: application/json' \
--data '{
    "userId": 1,
    "side": "CASH_IN",
    "amount": {
        "amount": 50000.00,
        "currency": "ARS"
    }
}'
```
Ejemplo de respuesta (`201 Created` — retorna `TransferOrderApi` con `amount` y `status: "FILLED"`, omitiendo `instrument`, `type`, `quantity` y `price`):
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

#### E. Transferencia Saliente / Retiro de Dinero (`CASH_OUT` usando `amount` — sin `instrumentId`, `type`, `quantity` ni `price`)
Valida que el monto solicitado no exceda el `availableCash` del usuario (retornando `409 BusinessRuleViolationProblem` en caso de fondos insuficientes):
```bash
curl --location 'http://localhost:8080/orders' \
--header 'Idempotency-Key: 550e8400-e29b-41d4-a716-446655440005' \
--header 'Content-Type: application/json' \
--data '{
    "userId": 1,
    "side": "CASH_OUT",
    "amount": {
        "amount": 20000.00,
        "currency": "ARS"
    }
}'
```

### 4. Cancelar una Orden Pendiente (`POST /orders/{orderId}/cancel`)
Solo las órdenes con estado `NEW` pueden ser transicionadas a `CANCELLED`:
```bash
curl --location --request POST 'http://localhost:8080/orders/5/cancel'
```
