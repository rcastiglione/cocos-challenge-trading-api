# Plan 015: Pre-Submission Audit (Security, Clean Code, 12-Factor Config, Logging & README Polish)

**Status:** Completed (`mvn clean verify` Green — Ready for GitHub Push)
**Context:** Cocos Challenge Trading API
**Architecture:** Multi-Module Pragmatic Service-Driven Architecture (Java 25 LTS, Spring Boot 4.0.9, PostgreSQL 16 + Flyway, Redis 7, Testcontainers, JaCoCo)

---

## 1. Scope & Audit Pillars

Auditoría final pre-entrega (*Zero New Features* — solo limpieza, hardening, configuración 12-Factor, seguridad de logs/excepciones y documentación ejecutiva):

1. **Security & Dependency Audit (`pom.xml`)**
2. **Code Quality & Clean Code (*Chapa y Pintura*)**
3. **12-Factor App & Configuration**
4. **Logging & Exception Safety**
5. **Final `README.md` Polish & JaCoCo Coverage Metrics**

---

## 2. Findings & Applied Fixes

### 2.1. Security & Dependency Audit (`pom.xml`)
- **`pom.xml` (Parent)**:
  - Eliminada propiedad sin uso `flyway.version` (gestionada por `spring-boot-starter-parent` `4.0.9`).
  - Centralizada la versión de `io.swagger.core.v3:swagger-annotations-jakarta` (`2.2.21`) en `<dependencyManagement>`.
  - Conservada `testcontainers.version` (`1.21.1`) requerida por los artefactos `org.testcontainers:junit-jupiter` y `org.testcontainers:postgresql`.
- **`cocos-trading-contract/pom.xml`**:
  - Eliminada versión hardcodeada en `swagger-annotations-jakarta` (heredada del parent POM).
- **`cocos-trading-service/pom.xml`**:
  - Eliminada dependencia redundante `org.flywaydb:flyway-core` (provista transitivamente por `spring-boot-starter-flyway`).

### 2.2. Code Quality & Clean Code
- **Unused Imports**: Eliminado `import java.time.Duration;` sin uso en `OrderServiceTest.java`.
- **Indentation & Formatting**: Normalizada indentación de 8 espacios a estándar de 4 espacios en:
  - `OrderService.java`
  - `OrderServiceTest.java`
  - `PortfolioAndInstrumentControllerIT.java`
- **Obsolete `// TODO` Cleanup**: Reemplazados los 16 comentarios `// TODO` repetitivos sobre `nullable = false` por documentación concisa a nivel de Javadoc de clase en:
  - `InstrumentJpaEntity.java`
  - `MarketDataJpaEntity.java`
  - `UserJpaEntity.java`
  - `OrderJpaEntity.java`

### 2.3. 12-Factor App & Configuration
- **`application-prod.yml`**: Eliminadas credenciales hardcodeadas de NeonDB; ahora exige estrictamente `${DB_URL}`, `${DB_USERNAME}` y `${DB_PASSWORD}` por variables de entorno.
- **`docker-compose.yml`**: Parametrizadas credenciales de PostgreSQL (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`) y Grafana (`GF_SECURITY_ADMIN_USER`, `GF_SECURITY_ADMIN_PASSWORD`) con valores por defecto seguros para entorno local.
- **`application.yml`**: Eliminada propiedad explícita `spring.jpa.properties.hibernate.dialect` (auto-detectada por Hibernate sin warnings).

### 2.4. Logging & Exception Safety
- **`GlobalExceptionHandler.java`**: Actualizado `handleMessageNotReadableException` para registrar únicamente `exception.getMessage()` en nivel `WARN` ante JSON malformado (`400 Bad Request`), evitando volcar stack traces innecesarios en logs de errores de cliente. Confirmado que ninguna respuesta HTTP expone stack traces internos de Java.

### 2.5. Final `README.md` Polish & JaCoCo Coverage Metrics
- Agregados badges y tabla de **Test Coverage (JaCoCo)** en la cabecera de `README.md`.
- Agregadas secciones **Quick Start / How to Run** (`docker compose up --build`) y **How to Run Tests & Coverage** (`mvn clean verify`) en la parte superior.
- Actualizada documentación de arquitectura para reflejar exclusivamente el estado final (`Long quantity` / `BIGINT`, índices Flyway V3–V5, configuración 12-Factor).

---

## 3. Verification Results (`mvn clean verify` — JDK 25)

| Check | Result | Detail |
| :--- | :--- | :--- |
| **Build & Package** | `BUILD SUCCESS` | `cocos-trading-contract` + `cocos-trading-service` |
| **Unit Tests (Surefire)** | `26 / 26` Passed | 0 Failures, 0 Errors, 0 Skipped |
| **Integration Tests (Failsafe + Testcontainers)** | `18 / 18` Passed | 0 Failures, 0 Errors, 0 Skipped |
| **Total Test Suite** | **`44 / 44` Passed** | 100% Green |
| **JaCoCo Line Coverage** | **`85.57%`** | `599 / 700` lines |
| **JaCoCo Branch Coverage** | **`62.15%`** | `225 / 362` branches |
| **JaCoCo Method Coverage** | **`87.59%`** | `120 / 137` methods |
| **JaCoCo Instruction Coverage** | **`84.30%`** | `2717 / 3223` instructions |

### 2.6. JaCoCo Exclusions (`cocos-trading-service/pom.xml`)
- Configurado `<excludes>` en `jacoco-maven-plugin` (`prepare-agent` y `report`) para excluir clases sin lógica de negocio:
  `**/*RequestApi.class`, `**/*Api.class`, `**/*JpaEntity.class`, `**/*Config.class`, `**/*Exception.class`, `**/*Mapper.class`, `**/*Constants.class`, `**/*Application.class` (37 clases analizadas).

---

## 4. Production-Readiness Roadmap (Pending Approval — Design Only)

> Las siguientes mejoras de concurrencia y resiliencia operativa quedan diseñadas para evaluación o iteración post-challenge sin haber modificado el código actual:

### 4.1. Concurrency Control & Double-Spend Prevention (`PESSIMISTIC_WRITE`)
- **Diagnóstico (*TOCTOU Race Condition*)**: El lock en Redis por `Idempotency-Key` protege contra reintentos idénticos, pero no serializa dos órdenes distintas enviadas en el mismo milisegundo por el mismo usuario con diferentes `Idempotency-Key` (ambos hilos podrían leer el mismo saldo bajo `READ COMMITTED` y generar *Double-Spending*).
- **Propuesta**:
  1. Agregar `@Lock(LockModeType.PESSIMISTIC_WRITE)` en `SpringDataUserRepository.findAndLockById(Long id)` (`SELECT ... FROM users WHERE id = ? FOR UPDATE`), serializando transacciones por `userId` sin contención global.
  2. Exponer `findAndLockById(Long id)` en `UserRepositoryPort` / `UserRepositoryAdapter` e invocarlo al inicio de `OrderService.executePlaceOrder(...)` dentro de `@Transactional`.
  3. Validar con test de concurrencia multi-hilo en `OrderControllerIT` (2 `MARKET BUY` concurrentes con distinta `Idempotency-Key` consumiendo el 100% del efectivo: 1 `FILLED`, 1 `REJECTED`).

### 4.2. Operational Resilience (`Graceful Shutdown` & `Actuator`)
- `spring-boot-starter-actuator` y `/actuator/health` (`liveness`/`readiness` probes) ya están activos.
- **Propuesta**: Agregar `server.shutdown: graceful` y `spring.lifecycle.timeout-per-shutdown-phase: 30s` en `application.yml`, alineando `stop_grace_period: 35s` en `docker-compose.yml` para proteger transacciones en vuelo ante `SIGTERM`.

