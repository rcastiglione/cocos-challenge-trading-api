# Observability Setup (Prometheus & Grafana)

This document outlines the architecture and implementation plan for adding a production-grade **Observability & Telemetry Layer** to the **Cocos Challenge Trading API**. By integrating **Spring Boot Actuator**, **Micrometer (`micrometer-registry-prometheus`)**, **Micrometer Tracing (`micrometer-tracing-bridge-brave`)**, **Prometheus**, and **Grafana** into our multi-container environment, we gain real-time visibility into JVM health, HTTP request SLIs/SLOs, distributed trace correlation (`traceId` / `spanId`) across console logs, database connection pool utilization (HikariCP), and high-cardinality-safe custom financial domain metrics (order execution latency and order throughput by status, side, and type).

---

## Step 1: Spring Boot Actuator, Micrometer & Distributed Tracing

### 1.1 Maven Dependency Additions (`cocos-trading-service/pom.xml`)
To keep `cocos-trading-contract` 100% framework-agnostic, all observability and distributed tracing dependencies are scoped strictly to [cocos-trading-service/pom.xml](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/pom.xml) (versions managed by `spring-boot-starter-parent` `3.3.0`):
- **`org.springframework.boot:spring-boot-starter-actuator`**: Provides production-ready operational endpoints (`/actuator/health`, `/actuator/info`, `/actuator/metrics`) and auto-configures core Micrometer instrumentation (JVM memory/GC, CPU, Tomcat threads, HikariCP connection pools, and Spring MVC HTTP server request observations).
- **`io.micrometer:micrometer-registry-prometheus`**: Bridges Micrometer's `MeterRegistry` to the Prometheus text exposition format, exposing `/actuator/prometheus` for pull-based scraping.
- **`io.micrometer:micrometer-tracing-bridge-brave`**: Bridges Micrometer Observation / Tracing APIs to OpenZipkin Brave (supporting W3C `traceparent` context propagation) and automatically populates SLF4J MDC fields (`traceId` and `spanId`) on every incoming HTTP request and internal observation without requiring an external Zipkin/Jaeger container.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
```

### 1.2 Actuator, Prometheus & Distributed Tracing Configuration (`application.yml`)
Update [application.yml](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/resources/application.yml) to expose the operational and telemetry endpoints, enable 100% trace sampling (`probability: 1.0`), format console logs with `[application,traceId,spanId]` correlation, and configure histogram buckets for accurate latency percentile calculation (`p50`, `p95`, `p99`) in Prometheus:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
    prometheus:
      enabled: true
  tracing:
    sampling:
      probability: 1.0
  metrics:
    tags:
      application: ${spring.application.name}
    distribution:
      percentiles-histogram:
        http.server.requests: true
        cocos.orders.execution.latency: true

logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

- **Endpoint Exposure**: Explicitly exposes `health`, `info`, `metrics`, and `prometheus` over HTTP under `/actuator/*` while keeping sensitive actuator endpoints (such as `env`, `beans`, or `heapdump`) disabled by default following SRE security best practices.
- **Distributed Tracing in Console Logs (`traceId` & `spanId`)**: With `micrometer-tracing-bridge-brave` and `management.tracing.sampling.probability: 1.0`, every HTTP request automatically generates (or propagates via W3C `traceparent`) a `traceId` and `spanId` injected into SLF4J's MDC. Every log statement—such as `[order] Placing order from command=...` or error stack traces—prints `INFO [cocos-trading-service,668f4b2e9c1d3a4b5e6f7a8b9c0d1e2f,9c1d3a4b5e6f7a8b]`, allowing SREs to correlate all log lines belonging to a specific order execution or failure immediately.
- **Liveness & Readiness Probes**: Enables Kubernetes/container-ready health groups (`/actuator/health/liveness` and `/actuator/health/readiness`) and database health indicators (`db`).
- **Common Metric Tags**: Automatically attaches `application="cocos-trading-service"` to every time series exported to Prometheus.

---

## Step 2: Custom Business Metrics (`TradingMetricsService` & `OrderService` Instrumentation)

### 2.1 Telemetry Design & Cardinality Control
In a financial trading engine, infrastructure metrics alone are insufficient—SREs and product teams need real-time telemetry on **order flow** and **execution latency**. We register custom Micrometer metrics using bounded, low-cardinality tag dimensions (never tagging unbounded values like `userId` or `orderId`):

1. **Order Throughput Counter (`cocos.orders.processed.total` -> exported as `cocos_orders_processed_total`)**:
   - **Type**: `io.micrometer.core.instrument.Counter`
   - **Description**: Total number of trading and cash transfer orders processed by the engine.
   - **Tags**:
     - `status`: `FILLED` | `REJECTED` | `NEW` | `CANCELLED`
     - `type`: `MARKET` | `LIMIT`
     - `side`: `BUY` | `SELL` | `CASH_IN` | `CASH_OUT`
   - **Operational Value**: Enables instant calculation of order throughput (`rate(cocos_orders_processed_total[1m])`), rejection ratio alerts (e.g., spikes in `status="REJECTED"` due to insufficient funds/shares), and order book composition (`MARKET` vs. `LIMIT`).

2. **Order Execution Latency Timer (`cocos.orders.execution.latency` -> exported as `cocos_orders_execution_latency_seconds`)**:
   - **Type**: `io.micrometer.core.instrument.Timer`
   - **Description**: End-to-end execution duration of `OrderService.placeOrder(...)` (covering user/instrument validation, latest market data lookup, account ledger balance calculation, strategy evaluation, and JPA persistence).
   - **Tags**:
     - `status`: `FILLED` | `REJECTED` | `NEW`
     - `type`: `MARKET` | `LIMIT`
     - `side`: `BUY` | `SELL` | `CASH_IN` | `CASH_OUT`
   - **Operational Value**: Tracks `count`, `sum`, `max`, and histogram buckets (`cocos_orders_execution_latency_seconds_bucket`) so Grafana and Prometheus Alertmanager can evaluate real-time `p50`, `p95`, and `p99` order placement latency via `histogram_quantile(...)`.

### 2.2 Architectural Decision: Telemetry Encapsulation via `TradingMetricsService`
To maintain strict **Separation of Concerns (SoC)** and prevent Micrometer builder boilerplate, metric names, and tag constants from polluting core application use cases:
- **Dedicated Telemetry Component ([TradingMetricsService.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/service/TradingMetricsService.java))**:
  - Encapsulates `MeterRegistry`, metric names (`ORDERS_PROCESSED_METRIC`, `ORDER_EXECUTION_LATENCY_METRIC`), and private tag keys (`TAG_STATUS`, `TAG_TYPE`, `TAG_SIDE`).
  - Exposes intention-revealing, domain-driven methods:
    - `Timer.Sample startOrderTimer()`
    - `void stopOrderTimer(Timer.Sample sample, String status, String type, String side)`
    - `void recordOrderProcessed(String status, String type, String side)`
- **Clean Delegation in [OrderService.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/main/java/com/cocos/trading/application/service/OrderService.java)**:
  - `OrderService` injects `TradingMetricsService` (instead of `MeterRegistry` directly) and delegates timer lifecycle and counter recording through clean one-line calls in `placeOrder(...)` and `cancelOrder(...)`, keeping the transactional orchestration completely free of magic strings and Micrometer `Counter.builder`/`Timer.builder` noise.
- **Test Updates**:
  - Update [OrderServiceTest.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/test/java/com/cocos/trading/application/service/OrderServiceTest.java) to construct `new TradingMetricsService(new SimpleMeterRegistry())` and assert that counters (`FILLED`, `REJECTED`, `NEW`, `CANCELLED`) and execution timers are recorded accurately across all order scenarios.
  - Verify in [OrderControllerIT.java](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/cocos-trading-service/src/it/java/com/cocos/trading/controller/OrderControllerIT.java) that `GET /actuator/prometheus` returns `200 OK` and exposes `cocos_orders_processed_total` and `cocos_orders_execution_latency_seconds`.

---

## Step 3: Docker Compose Expansion (Prometheus & Grafana)

### 3.1 Prometheus Configuration (`docker/prometheus/prometheus.yml`)
Create `docker/prometheus/prometheus.yml` to configure Prometheus to scrape the Trading API's `/actuator/prometheus` endpoint every **5 seconds** over the internal Docker bridge network (`cocos-network`):

```yaml
global:
  scrape_interval: 5s
  evaluation_interval: 5s

scrape_configs:
  - job_name: 'cocos-trading-api'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['cocos-trading-api:8080']
        labels:
          application: 'cocos-trading-service'
          environment: 'local'
```

### 3.2 Zero-Touch Grafana Provisioning (`docker/grafana/`)
To make the reviewer experience effortless right after `docker compose up --build`, we will include auto-provisioning files under `docker/grafana/provisioning/`:
- **`datasources/datasource.yml`**: Automatically registers `http://cocos-prometheus:9090` as the default Prometheus datasource in Grafana (no manual UI setup needed).
- **`dashboards/dashboard.yml` & `cocos-trading-dashboard.json`**: Pre-loads a ready-to-use **"Cocos Trading Engine — Real-Time Observability"** dashboard displaying:
  - Total Orders Processed by Status (`FILLED`, `REJECTED`, `NEW`, `CANCELLED`)
  - Order Throughput by Side (`BUY`, `SELL`, `CASH_IN`, `CASH_OUT`)
  - Order Execution Latency (`p50`, `p95`, `p99` in ms)
  - JVM Heap Usage & HTTP Request Rate

### 3.3 Updating Root `docker-compose.yml`
Expand [docker-compose.yml](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/docker-compose.yml) to include two new services (`prometheus` and `grafana`) attached to `cocos-network`:

```yaml
  prometheus:
    image: prom/prometheus:v2.53.0
    container_name: cocos-prometheus
    depends_on:
      - cocos-trading-api
    ports:
      - "9090:9090"
    volumes:
      - ./docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus_data:/prometheus
    command:
      - "--config.file=/etc/prometheus/prometheus.yml"
      - "--storage.tsdb.path=/prometheus"
      - "--web.enable-lifecycle"
    networks:
      - cocos-network

  grafana:
    image: grafana/grafana:11.1.0
    container_name: cocos-grafana
    depends_on:
      - prometheus
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    volumes:
      - grafana_data:/var/lib/grafana
      - ./docker/grafana/provisioning:/etc/grafana/provisioning:ro
    networks:
      - cocos-network
```
*(Named volumes `prometheus_data` and `grafana_data` will also be declared under the top-level `volumes:` section).*

---

## Step 4: `README.md` Update (Observability, Distributed Tracing & Alerting Strategy)

Update [README.md](file:///Users/richard/environment/workspace-richard/cocos-challenge-trading-api/README.md) (consistent with the repository's LatAm Fintech Spanglish style) to showcase the **Observability Stack** as a production-grade differentiator (*"Nice to have"*):
1. **Tech Stack & Repository Tree**:
   - Add **Spring Boot Actuator, Micrometer (`micrometer-registry-prometheus`) & Micrometer Tracing (`micrometer-tracing-bridge-brave`)**, **Prometheus**, and **Grafana** to the Tech Stack list and directory tree (`docker/prometheus/prometheus.yml`, `docker/grafana/`).
2. **New Section — Observability, Distributed Tracing & Alerting Stack (Actuator + Micrometer + Prometheus + Grafana)**:
   - **Custom Business Metrics**: Document `cocos_orders_processed_total` (tagged by `status`: `FILLED`, `REJECTED`, `NEW`, `CANCELLED`; `type`; and `side`) and `cocos_orders_execution_latency_seconds` (histogram timer for end-to-end order execution).
   - **Distributed Tracing (`traceId` & `spanId`)**: Explain how `micrometer-tracing-bridge-brave` injects W3C trace context (`[cocos-trading-service,traceId,spanId]`) into every console log line so engineers can isolate and debug specific order failures across concurrent threads in production.
   - **Production Alerting Strategy & Error Budgets (Prometheus Alertmanager / Grafana Alerts)**: Explicitly document how these custom telemetry signals are designed to drive automated on-call paging (**PagerDuty / Slack**) when SLOs or error budgets are threatened:
     - **High Order Rejection Rate Alert (`status="REJECTED"`)**: Triggers a warning/page when the ratio of rejected orders (`sum(rate(cocos_orders_processed_total{status="REJECTED"}[5m])) / sum(rate(cocos_orders_processed_total[5m])) > 0.15`) spikes abnormally, signaling potential upstream pricing anomalies, liquidity issues, or client integration regressions.
     - **Order Execution Latency SLO Breach (`p99` Timer)**: Triggers a high-severity page when `histogram_quantile(0.99, sum(rate(cocos_orders_execution_latency_seconds_bucket[5m])) by (le)) > 0.250` (250ms SLO threshold), protecting the trading engine's latency error budget before database contention or lock waits degrade the user experience.
   - **Quick-Access Endpoints & Dashboards**:
     - **Actuator Prometheus Scrape Endpoint**: `http://localhost:8080/actuator/prometheus`
     - **Actuator Health Probe**: `http://localhost:8080/actuator/health`
     - **Prometheus UI**: `http://localhost:9090` (scrape interval: `5s`)
     - **Grafana UI**: `http://localhost:3000` (`admin` / `admin`) with zero-touch pre-provisioned Prometheus datasource and Trading Engine dashboard.
