# Plan 016: Double-Spending Prevention via User-Level Pessimistic Locking (`SELECT ... FOR UPDATE`)

**Status:** Pending Approval (Design Only — No Code Modified Yet)
**Context:** Cocos Challenge Trading API
**Architecture:** Multi-Module Pragmatic Hexagonal Architecture (Java 25 LTS, Spring Boot 4.0.9, PostgreSQL 16 + Flyway, Redis 7, Testcontainers)

---

## 1. The Double-Spending Problem (Thought / Diagnosis)

### 1.1. Time-of-Check to Time-of-Use (TOCTOU) Race Condition
Actualmente, nuestro motor transaccional cuenta con un mecanismo de idempotencia híbrido (Redis In-Flight Lock + PostgreSQL Safe Replay) indexado por `Idempotency-Key`. Este diseño protege eficazmente contra **reintentos de red idénticos** (*network retries*) o *double-clicks* que reutilizan el mismo UUID.

Sin embargo, existe una vulnerabilidad crítica de **TOCTOU (*Time-of-Check to Time-of-Use*)** cuando un mismo usuario envía **múltiples órdenes concurrentes con diferentes `Idempotency-Key`s**:

1. **Estado inicial**: El usuario tiene `$10,000.00 ARS` de `availableCash`.
2. **Ráfaga concurrente**: El cliente dispara simultáneamente dos peticiones `POST /orders` (`MARKET BUY`) por `$10,000.00 ARS` cada una, con `Idempotency-Key: uuid-A` y `Idempotency-Key: uuid-B`.
3. **Bypass de Redis**: Como los UUIDs son distintos, Redis otorga ambos *locks* de idempotencia (`idempotency:orders:uuid-A` y `idempotency:orders:uuid-B`) en paralelo.
4. **Lectura sucia bajo `READ COMMITTED`**: Ambos hilos (`Thread-1` y `Thread-2`) ejecutan `AccountLedgerCalculator.calculateAvailableCash(...)` casi al mismo milisegundo. Ninguna orden ha hecho `COMMIT` todavía, por lo que **ambos hilos leen `$10,000.00 ARS` disponibles**.
5. **Doble gasto (*Double-Spending*)**: Ambos hilos validan exitosamente (`$10,000 >= $10,000`), persisten sus órdenes `FILLED` y hacen `COMMIT`. El usuario termina con `$20,000` en acciones y un saldo efectivo de **`-$10,000.00 ARS`**.

### 1.2. ¿Por qué `SELECT ... FOR UPDATE` sobre la fila `User` es el Estándar de la Industria?
Para eliminar el TOCTOU sin destruir el *throughput* global del sistema:
- **Serialización estricta por usuario (*Per-User Serialization*)**: Al adquirir un *row-level exclusive write lock* (`SELECT ... FROM users WHERE id = ? FOR UPDATE`) sobre el registro del usuario en PostgreSQL, cualquier otra transacción concurrente para el **mismo `userId`** queda bloqueada a nivel de base de datos hasta que la primera transacción termine (`COMMIT` o `ROLLBACK`).
- **Cero contención global (*Zero Global Contention*)**: El bloqueo afecta únicamente a la fila `users.id = ?` involucrada. Los demás usuarios operan en paralelo con latencia cero de espera.
- **Menor complejidad que `SERIALIZABLE`**: A diferencia del nivel de aislamiento `SERIALIZABLE` (que provoca errores `40001 serialization_failure` y exige lógica compleja de *retry* en la aplicación), `PESSIMISTIC_WRITE` bajo `READ COMMITTED` serializa limpiamente en PostgreSQL y garantiza que el segundo hilo lea el estado ya confirmado (*committed*) por el primer hilo.

---

## 2. JPA & Repository Layer Implementation

### 2.1. `SpringDataUserRepository`
Archivo: `cocos-trading-service/src/main/java/com/cocos/trading/repository/jpa/SpringDataUserRepository.java`

Agregar un método dedicado para lectura con bloqueo pesimista exclusivo y *timeout* acotado para prevenir *deadlocks* o esperas infinitas ante caídas de conexiones:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
@Query("SELECT u FROM UserJpaEntity u WHERE u.id = :id")
Optional<UserJpaEntity> findAndLockById(@Param("id") Long id);
```

- `@Lock(LockModeType.PESSIMISTIC_WRITE)`: Instruye a Hibernate a emitir `SELECT ... FROM users WHERE id = ? FOR UPDATE` en PostgreSQL.
- `@QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})`: Configura un *lock timeout* de `3000 ms` (3 segundos) para abortar de forma segura si un hilo retiene el *lock* demasiado tiempo, evitando agotamiento del *connection pool* (HikariCP).
- Se mantiene `findById(Long id)` intacto para lecturas *read-only* (como `PortfolioService.getPortfolio`), evitando locks innecesarios en endpoints `GET`.

### 2.2. `UserRepositoryPort` & `UserRepositoryAdapter`
Propagar el nuevo contrato respetando la arquitectura hexagonal:

- **`UserRepositoryPort`** (`cocos-trading-service/src/main/java/com/cocos/trading/application/port/out/UserRepositoryPort.java`):
  - Declarar `Optional<UserJpaEntity> findAndLockById(Long id);`.
- **`UserRepositoryAdapter`** (`cocos-trading-service/src/main/java/com/cocos/trading/repository/UserRepositoryAdapter.java`):
  - Implementar `findAndLockById(Long id)` delegando a `springDataUserRepository.findAndLockById(id)`.

---

## 3. Transactional Service Layer Integration

### 3.1. `OrderService.executePlaceOrder(...)`
Archivo: `cocos-trading-service/src/main/java/com/cocos/trading/application/service/OrderService.java`

Actualmente, `executePlaceOrder` (invocado dentro de `placeOrder`, anotado con `@Transactional`) obtiene el usuario mediante `userRepositoryPort.findById(command.userId())` en la línea 111.

**Cambio requerido:**
Reemplazar `userRepositoryPort.findById(command.userId())` por **`userRepositoryPort.findAndLockById(command.userId())`** al **inicio absoluto** de `executePlaceOrder`, antes de consultar `marketData`, `filledTradeOrders`, `filledTransferOrders` o calcular `availableCash` / `availableShares`:

```java
UserJpaEntity user = userRepositoryPort.findAndLockById(command.userId())
        .orElseThrow(() -> new ResourceNotFoundException("User", command.userId()));
```

**Ciclo de vida del Lock (ReAct Flow):**
1. `Thread-1` y `Thread-2` pasan el *Fast-Fail Lock* de Redis (tienen `Idempotency-Key` distintos).
2. `Thread-1` entra a `@Transactional placeOrder` $\rightarrow$ `executePlaceOrder` $\rightarrow$ ejecuta `findAndLockById(userId)`. PostgreSQL le otorga el `FOR UPDATE` lock sobre la fila del usuario.
3. `Thread-2` llega a `findAndLockById(userId)` y **queda en espera (*blocked*)** en PostgreSQL hasta que `Thread-1` libere el lock.
4. `Thread-1` consulta órdenes `FILLED`, calcula `availableCash = $10,000`, ejecuta estrategia (`FILLED`), persiste la orden y hace `COMMIT` (liberando el lock).
5. `Thread-2` adquiere el lock, consulta órdenes `FILLED` (ya ve la orden de `Thread-1` confirmada bajo `READ COMMITTED`), calcula `availableCash = $0`, y su orden es evaluada como **`REJECTED`** (o `BusinessRuleViolationException` / `409 Conflict` si es `CASH_OUT`), garantizando saldo `>= 0`.

> **Nota sobre Unit Tests:** Actualizar `OrderServiceTest.java` para stubbear `userRepositoryPort.findAndLockById(...)` en lugar de `findById(...)` donde corresponda.

---

## 4. Concurrency Integration Testing

### 4.1. Test Multi-Hilo Determinístico (`OrderControllerIT` o `ConcurrencyIT`)
Archivo sugerido: `cocos-trading-service/src/it/java/com/cocos/trading/controller/OrderControllerIT.java` (o nueva clase dedicada `ConcurrencyIT` extendiendo `TradingApplicationIT`).

**Diseño del Test:**
1. **Setup**: Usuario con saldo exacto disponible (ej. `$50,000.00 ARS` ya sembrados en `OrderControllerIT`, o `$10,000.00 ARS` en un escenario dedicado) e instrumento `PAMP` con `close = 925.85` (o precio entero exacto para consumir el 100% del saldo).
2. **Sincronización de Hilos**:
   - `ExecutorService executor = Executors.newFixedThreadPool(2);`
   - `CountDownLatch readyLatch = new CountDownLatch(2);` y `CountDownLatch startLatch = new CountDownLatch(1);` para garantizar que ambos hilos disparen `POST /orders` simultáneamente en el mismo milisegundo.
3. **Payloads Concurrentes**:
   - Ambos hilos envían un `MARKET BUY` por el **100% del saldo disponible**, cada uno con su propio `Idempotency-Key` (`UUID.randomUUID()`).
4. **Aserciones Estrictas**:
   - Ambas respuestas HTTP deben retornar **`201 Created`** (sin excepciones 500 ni *deadlocks*).
   - Exactamente **1 orden** debe retornar `status: FILLED`.
   - Exactamente **1 orden** debe retornar `status: REJECTED` (por fondos insuficientes tras la serialización).
   - Consultar `GET /portfolios/{userId}` o calcular el *ledger* en base de datos al finalizar y verificar que `availableCash >= 0` (nunca negativo).

---

## 5. README Documentation

### 5.1. Actualización de `README.md` (Sección 3.7 / "Double-Spend Prevention")
Actualizar la sección `### 3.7. True Idempotency y Control de Concurrencia Híbrido...` (o agregar una subsección explícita de **Double-Spend Prevention**) diferenciando claramente las dos capas de protección:

- **Capa 1 — Redis Distributed In-Flight Lock (`Idempotency-Key`)**: Protege contra **reintentos de red idénticos y *double-clicks*** que comparten el mismo `Idempotency-Key` UUID (*fast-fail* en memoria con `409 Conflict` + *Safe Replay* desde PostgreSQL).
- **Capa 2 — PostgreSQL User-Level Pessimistic Locking (`PESSIMISTIC_WRITE` / `SELECT ... FOR UPDATE`)**: Protege contra **mutaciones concurrentes de estado** cuando un mismo usuario envía múltiples órdenes distintas (con diferentes `Idempotency-Key`s) en el mismo milisegundo. Serializa las transacciones a nivel de fila de usuario con *lock timeout* de `3000 ms` y cero contención global, erradicando la condición de carrera **TOCTOU (*Double-Spending*)**.

---

## 6. Verification Checklist (Post-Approval)
- [ ] Implementar `findAndLockById` en `SpringDataUserRepository`, `UserRepositoryPort` y `UserRepositoryAdapter`.
- [ ] Integrar `findAndLockById` al inicio de `OrderService.executePlaceOrder(...)`.
- [ ] Actualizar `OrderServiceTest.java` y agregar test de concurrencia multi-hilo en Integration Tests.
- [ ] Actualizar `README.md` documentando Redis vs. Pessimistic Locking.
- [ ] Ejecutar `mvn clean verify` (Surefire + Failsafe Testcontainers + JaCoCo) y validar 100% verde.

