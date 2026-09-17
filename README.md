# TracePilot

TracePilot is a Spring Boot observability project that captures distributed traces from three microservices, sends them through the OpenTelemetry Collector, visualizes them in Jaeger, and stores real OTLP spans in PostgreSQL for analytics.

## Architecture

```text
                         ┌───────────────┐
                         │   Jaeger UI   │
                         │    :16686     │
                         └───────▲───────┘
                                 │ OTLP gRPC
                                 │
┌───────────────┐      ┌─────────┴──────────┐      ┌──────────────────┐
│ order-service │─────▶│ OpenTelemetry      │─────▶│ TracePilot       │
│     :8081     │ OTLP │ Collector          │ OTLP │ Backend :8080    │
└───────┬───────┘      │ :4317 and :4318    │ HTTP │ POST /v1/traces │
        │              └────────────────────┘      └────────┬─────────┘
        ├──────────────▶ payment-service :8082              │
        └──────────────▶ inventory-service :8083            ▼
                                                        PostgreSQL
                                                          :5432
```

The Collector exports each trace to both Jaeger and TracePilot. TracePilot stores every span in `span_records` and projects server spans into `trace_records`, which powers the existing analytics endpoints.

## Repository layout

| Directory | Purpose |
|---|---|
| `tracepilot-backend` | OTLP ingestion, PostgreSQL persistence, trace CRUD, and analytics APIs |
| `order_service` | Entry service that calls inventory and payment |
| `payment_service` | Slow and failed payment scenarios |
| `inventory-service` | Inventory endpoint used by the order flow |
| `otel-collector` | Collector and Jaeger Docker configuration |
| `scripts` | Local setup helpers |

## Requirements

- Java 21
- Docker Desktop with Docker Compose
- `curl`

## Local setup

Run the following commands from the repository root.

### 1. Configure the local database password

```bash
cp .env.example .env
```

Edit `.env` and replace the example value. The file is ignored by Git.

### 2. Download the OpenTelemetry Java Agent

```bash
./scripts/download-otel-agent.sh
```

The downloaded JAR is copied into each microservice's `otel` directory and is not committed to Git.

### 3. Start PostgreSQL and Redis

```bash
docker compose --env-file .env \
  -f tracepilot-backend/docker-compose.yml up -d
```

### 4. Start TracePilot Backend

```bash
set -a
source .env
set +a
cd tracepilot-backend
./mvnw spring-boot:run
```

The backend starts on `http://localhost:8080`.

### 5. Start the Collector and Jaeger

From another terminal at the repository root:

```bash
docker compose -f otel-collector/docker-compose.yml up -d
```

Jaeger is available at `http://localhost:16686`.

### 6. Package and start the microservices

Package each service:

```bash
(cd order_service && ./mvnw package)
(cd payment_service && ./mvnw package)
(cd inventory-service && ./mvnw package)
```

Start each service in a separate terminal:

```bash
cd inventory-service
java -javaagent:otel/opentelemetry-javaagent.jar \
  -Dotel.service.name=inventory-service \
  -Dotel.exporter.otlp.protocol=http/protobuf \
  -Dotel.exporter.otlp.endpoint=http://localhost:4318 \
  -Dotel.logs.exporter=none \
  -Dotel.metrics.exporter=none \
  -jar target/inventory-service-0.0.1-SNAPSHOT.jar
```

```bash
cd payment_service
java -javaagent:otel/opentelemetry-javaagent.jar \
  -Dotel.service.name=payment-service \
  -Dotel.exporter.otlp.protocol=http/protobuf \
  -Dotel.exporter.otlp.endpoint=http://localhost:4318 \
  -Dotel.logs.exporter=none \
  -Dotel.metrics.exporter=none \
  -jar target/payment-service-0.0.1-SNAPSHOT.jar
```

```bash
cd order_service
java -javaagent:otel/opentelemetry-javaagent.jar \
  -Dotel.service.name=order-service \
  -Dotel.exporter.otlp.protocol=http/protobuf \
  -Dotel.exporter.otlp.endpoint=http://localhost:4318 \
  -Dotel.logs.exporter=none \
  -Dotel.metrics.exporter=none \
  -jar target/order_service-0.0.1-SNAPSHOT.jar
```

## Try the trace flow

```bash
curl -i http://localhost:8081/order/test
```

The current order test intentionally calls `payment-service/payment/fail`, so an HTTP 500 response is expected. The resulting trace should appear in Jaeger and PostgreSQL.

Once you have a trace ID, query its complete ordered span list:

```bash
curl "http://localhost:8080/api/spans?traceId=TRACE_ID"
```

Analytics endpoints:

```text
GET /api/analytics/slow-traces
GET /api/analytics/failed-traces
GET /api/analytics/summary
GET /api/analytics/services
```

## Tests

```bash
cd tracepilot-backend
./mvnw test
```

The backend tests use an in-memory H2 database. They cover application startup, OTLP ingestion, server-span projection, status conversion, and gzip-compressed OTLP requests.

## Current milestone

Day 6 connects the real OpenTelemetry data stream to TracePilot:

- receives standard OTLP HTTP Protobuf at `POST /v1/traces`;
- stores trace IDs, span IDs, parent relationships, timing, status, and attributes;
- prevents duplicate span ingestion with a `(trace_id, span_id)` unique constraint;
- exports the same trace to Jaeger and TracePilot;
- calculates global trace counts using distinct trace IDs;
- supports complete span lookup through `GET /api/spans?traceId=...`.
