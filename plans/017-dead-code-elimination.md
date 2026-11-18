# Plan 017: Zero Dead Code Sweep & Elimination

**Status:** Pending Approval (Design Only — No Code Modified Yet)
**Context:** Cocos Challenge Trading API
**Architecture:** Multi-Module Pragmatic Hexagonal Architecture (Java 25 LTS, Spring Boot 4.0.9, PostgreSQL 16 + Flyway, Redis 7, Testcontainers)

---

## 1. Dead Code Audit (The Thought Process)

### 1.1. ¿Por qué Zero Dead Code es crítico en un Technical Challenge?
En una entrega *production-ready*, el código muerto (*dead code*: métodos inalcanzables, constantes sin uso, *DTOs* huérfanos, *imports* o variables residuales) genera ruido cognitivo y degrada la calidad percibida por un *senior reviewer*:

1. **Cohesión y Mantenibilidad (*High Cohesion & Maintainability*)**: Cada clase y método debe tener una razón clara de existir en el flujo actual del dominio. Mantener código legado de iteraciones previas induce a confusión sobre cuál es el camino canónico de ejecución.
2. **Integridad de Métricas de Cobertura (JaCoCo)**: Los métodos huérfanos aparecen con `0%` de cobertura en los reportes de JaCoCo, distorsionando la métrica real de *test coverage* del sistema.
3. **Higiene Arquitectónica (*Architectural Hygiene*)**: En un diseño hexagonal multi-módulo (`cocos-trading-contract` + `cocos-trading-service`), cualquier *port*, *mapper*, *DTO* o *helper* sin consumidores activos rompe la limpieza del grafo de dependencias.

### 1.2. Caso Confirmado: `ValidatorUtils.validatePositiveBigDecimal`
Durante la evolución hacia *Fintech Data Types* (Plan 013), migramos los campos monetarios de `BigDecimal` crudo al *Value Object* **`MonetaryAmount`**, reemplazando las validaciones numéricas genéricas por **`ValidatorUtils.validateMonetaryAmount(...)`**.

Como resultado:
- En `cocos-trading-service/src/main/java/com/cocos/trading/controller/validator/ValidatorUtils.java`, el método `validatePositiveBigDecimal(Errors, BigDecimal, String, String, String)` quedó completamente huérfano (sin *callers* en producción ni en tests, y marcado con `0%` de cobertura en JaCoCo).
- Su eliminación (junto con cualquier *import* residual como `java.math.BigDecimal` si deja de referenciarse en la clase) restaura la cohesión de `ValidatorUtils`.

---

## 2. Codebase Sweep & Elimination (The Action)

### 2.1. Alcance del Barrido (*Full Multi-Module Scan*)
Ejecutar una auditoría estática y de referencias cruzadas sobre ambos módulos del proyecto:
- **`cocos-trading-contract`**: *DTOs*, *records*, *enums*, interfaces de API, anotaciones de validación y constantes compartidas.
- **`cocos-trading-service`**: *Controllers*, *validators*, *application services*, *domain calculators/strategies*, *ports* (`in`/`out`), *adapters*, *JPA entities*, *Spring Data repositories*, *mappers*, *configuration classes*, *exception handlers* y *test suites* (`src/test` y `src/it`).

### 2.2. Categorías de Eliminación (*Elimination Targets*)
1. **Orphaned Helper / Utility Methods**:
   - Eliminar `ValidatorUtils.validatePositiveBigDecimal(...)` en `cocos-trading-service/src/main/java/com/cocos/trading/controller/validator/ValidatorUtils.java`.
   - Revisar otras clases `*Utils`, `*Helper` o `*Calculator` en busca de métodos *static* o de instancia sin referencias activas.
2. **Unused Private Methods & Fields**:
   - Detectar y remover métodos `private`, campos privados y constructores auxiliares que no tengan invocaciones dentro de su clase.
3. **Unused Public Methods in Internal Services / Ports / Adapters**:
   - Verificar métodos `public` en *services*, *repositories*, *mappers* y *adapters* internos que no formen parte de ningún *use case* ni *test* activo.
4. **Orphaned DTOs, Records & Constants**:
   - Identificar *records*, *DTOs*, *enums* o constantes (`static final`) sin consumidores en `cocos-trading-contract` ni `cocos-trading-service`.
5. **Unused Imports, Variables & Parameters**:
   - Limpiar *imports* huérfanos (incluyendo aquellos que queden sin uso tras borrar métodos como `validatePositiveBigDecimal`), variables locales no leídas y parámetros muertos.

### 2.3. Safeguard contra Falsos Positivos (*Reflection & Framework Entry Points*)
Antes de eliminar cualquier símbolo marcado como *unused* por análisis estático, validar que **no** sea invocado dinámicamente por frameworks o contratos externos:
- Endpoints REST (`@RestController`, `@RequestMapping` y sus *contract interfaces*).
- *Bean Validation*, *Jackson* (serialización/deserialización de *DTOs*), *MapStruct* (*mappers* generados en *compile-time*), *Spring Data JPA* (*derived query methods* / `@Query`) y *Spring* `@Bean` / `@ConfigurationProperties`.
- Métodos usados exclusivamente por *unit tests* o *integration tests* (evaluar si el método sigue siendo parte del contrato válido o si tanto el método como un *test* obsoleto deben depurarse).

---

## 3. Test Verification

Una vez completada la limpieza en `cocos-trading-contract` y `cocos-trading-service`:

1. **Compilación y Test Suite Completa**:
   ```bash
   mvn clean verify
   ```
   - Verifica compilación limpia de ambos módulos (incluyendo generación de fuentes *MapStruct* / *OpenAPI* si aplica).
   - Ejecuta el 100% de **Surefire** (*unit tests*), **Failsafe** (*integration tests* con Testcontainers: PostgreSQL + Redis) y reportes de **JaCoCo**.
2. **Criterio de Aceptación**:
   - Build **100% verde** sin errores ni *regressions*.
   - Confirmación explícita de que no se eliminó ningún código requerido en *runtime* o *compile-time*.
   - Revisión final de `git diff` para asegurar cambios quirúrgicos y cero archivos temporales residuales.

---

## 4. Verification Checklist (Post-Approval)
- [ ] Auditar `cocos-trading-contract` y `cocos-trading-service` en busca de métodos, constantes, *DTOs*, variables e *imports* sin uso.
- [ ] Eliminar `ValidatorUtils.validatePositiveBigDecimal(...)` y limpiar sus *imports* residuales.
- [ ] Eliminar el resto de *dead code* confirmado (respetando *framework entry points* y contratos públicos).
- [ ] Ejecutar `mvn clean verify` y confirmar build 100% verde (Surefire + Failsafe + JaCoCo).

