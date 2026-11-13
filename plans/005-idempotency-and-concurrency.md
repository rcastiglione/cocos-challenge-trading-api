# Spec: Implementación de Idempotencia y Concurrencia
**Status:** Completed
**Context:** Cocos Challenge Trading API
**Architecture:** Hexagonal / Clean Architecture (Java 21, Spring Boot 3.3, Redis 7)

## Objetivo
Implementar Idempotencia como feature arquitectónica de nivel Senior para hacer el proceso de creación de órdenes verdaderamente "production-ready". Al igual que en las pasarelas de pago, necesitamos prevenir el double-spending o la duplicación de órdenes (especialmente en `CASH_IN`/`CASH_OUT` y trades de mercado) si un cliente reintenta un request debido a un timeout de red o envía ráfagas concurrentes.

---

## Pasos de Implementación y Trazabilidad SDD

### 1. Actualización del Contrato de la API (API Contract Update)
*   Modificar el endpoint `POST /orders` para requerir un header mandatorio llamado `Idempotency-Key` (formato UUID).
*   Actualizar la documentación de Swagger/OpenAPI y los DTOs correspondientes en el módulo `cocos-trading-contract` para reflejar este nuevo requerimiento.
*   **Implementación:**
    *   [OrderContractApi.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-contract/src/main/java/com/cocos/trading/api/OrderContractApi.java): Define la constante `IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"` y documenta el parámetro `@Parameter(in = ParameterIn.HEADER, name = "Idempotency-Key", required = true) UUID idempotencyKey` utilizando únicamente tipos de la librería estándar (`java.util.UUID`) y anotaciones OpenAPI agnósticas de framework.
    *   [OrderCreateRequestApi.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-contract/src/main/java/com/cocos/trading/api/request/OrderCreateRequestApi.java): Actualiza `@Schema` y JavaDoc reflejando el requerimiento de idempotencia.
    *   [OrderController.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/controller/OrderController.java) & [GlobalExceptionHandler.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/controller/handler/GlobalExceptionHandler.java): Extrae `@RequestHeader(OrderContractApi.IDEMPOTENCY_KEY_HEADER) UUID idempotencyKey` y traduce cabeceras faltantes (`MissingRequestHeaderException`) o malformadas (`MethodArgumentTypeMismatchException`) en respuestas `400 Bad Request` (`BadRequestProblem` bajo RFC 7807).

### 2. Infraestructura de Redis (Redis Infrastructure)
*   Agregar un contenedor de Redis al archivo `docker-compose.yml` local.
*   Configurar un Testcontainer de Redis para los tests de integración (`TradingApplicationIT`).
*   Agregar la dependencia `spring-boot-starter-data-redis` en el `pom.xml` del servicio core.
*   **Implementación:**
    *   [cocos-trading-service/pom.xml](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/pom.xml): Agrega `org.springframework.boot:spring-boot-starter-data-redis`.
    *   [docker-compose.yml](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/docker-compose.yml): Incorpora el servicio `cocos-redis` (`redis:7-alpine`) con healthcheck (`redis-cli ping`), volumen persistente `redis_data`, y variables `REDIS_HOST`/`REDIS_PORT` inyectadas en `cocos-trading-api`.
    *   [application.yml](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/resources/application.yml): Configura `spring.data.redis.host` y `spring.data.redis.port`.
    *   [TradingApplicationIT.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/it/java/com/cocos/trading/TradingApplicationIT.java): Levanta un contenedor `redis:7-alpine` enlazado mediante `@ServiceConnection(name = "redis")` junto al contenedor de PostgreSQL 16.

### 3. Lógica de Idempotencia y Distributed Lock
*   Implementar la verificación de idempotencia utilizando Redis para almacenar la `Idempotency-Key` con un TTL de 24 horas.
*   **Lógica:** Si la clave ya existe en Redis, la API debe rechazar el request duplicado (`409 Conflict`).
*   Este distributed lock debe envolver de forma segura la transacción `placeOrder` del caso de uso.
*   **Implementación:**
    *   **Puerto de Salida Hexagonal** ([IdempotencyRepositoryPort.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/port/out/IdempotencyRepositoryPort.java)): Define `tryAcquire(UUID idempotencyKey, Duration ttl)` y `release(UUID idempotencyKey)` sin acoplar la capa de aplicación a Spring Data Redis.
    *   **Adaptador Redis** ([RedisIdempotencyAdapter.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/repository/RedisIdempotencyAdapter.java)): Utiliza `StringRedisTemplate.opsForValue().setIfAbsent("idempotency:orders:" + key, timestamp, Duration.ofHours(24))` (`SET ... NX EX 86400`), garantizando atomicidad tanto para ráfagas concurrentes como para reintentos de red durante 24 horas.
    *   **Orquestación Transaccional y Excepción de Dominio** ([OrderService.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/service/OrderService.java) & [DuplicateOrderException.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/domain/exception/DuplicateOrderException.java)): Envuelve `executePlaceOrder(command)` adquiriendo primero el lock distribuido en Redis; si la clave ya existe, lanza `DuplicateOrderException` (mapeada a `409 Conflict` / `BusinessRuleViolationProblem` con `title = "Duplicate Order"`). Si la ejecución falla con una excepción antes de confirmar la orden, libera la clave en el bloque `catch` (`idempotencyRepositoryPort.release(idempotencyKey)`).

### 4. TODO de Esquema de Base de Datos (Database Schema TODO)
*   En un entorno de producción real, se agregaría un constraint `UNIQUE(idempotency_key)` directamente a la tabla de órdenes en PostgreSQL.
*   **Acción:** Agregar un comentario `// TODO` muy visible en el código (cerca de la entidad u orquestador) explicando que estamos usando Redis para respetar el esquema de base de datos fijo provisto por el evaluador del challenge, pero que una restricción permanente a nivel DB sería agregada vía una futura migración (Flyway/Liquibase).
*   **Implementación:**
    *   Documentado de forma prominente en [OrderJpaEntity.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/repository/entity/OrderJpaEntity.java#L34-L42) y en [OrderService.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/service/OrderService.java#L45-L51).

### 5. Actualización del README.md (Design Decisions)
*   Agregar una nueva entrada en la sección "Decisiones de Diseño / Design Decisions" del [README.md](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/README.md#L126-L140) (**Sección 3.7: Idempotencia y Control de Concurrencia Distribuida (`Idempotency-Key` + Redis)**).
*   Actualizar ejemplos cURL en `README.md` y la colección Postman ([cocos-trading-api.postman_collection.json](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-api.postman_collection.json)) con `Idempotency-Key`.
*   **Verificación Automatizada:**
    *   Unit tests en [OrderServiceTest.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/test/java/com/cocos/trading/application/service/OrderServiceTest.java#L418-L511) (`shouldRejectDuplicateOrderWhenIdempotencyKeyAlreadyExists`, `shouldPreventDoubleSpendingWhenSameIdempotencyKeySubmittedConcurrently`).
    *   Integration tests con PostgreSQL + Redis Testcontainers en [OrderControllerIT.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/it/java/com/cocos/trading/controller/OrderControllerIT.java#L345-L473) (`shouldReturnBadRequestWhenIdempotencyKeyHeaderMissingOrInvalid`, `shouldStoreIdempotencyKeyInRedisAndRejectDuplicateRetry`, `shouldPreventConcurrentDoubleSpendingWithSameIdempotencyKey`).
