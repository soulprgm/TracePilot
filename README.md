# TracePilot

TracePilot is a complete distributed tracing demo and observability dashboard built with Spring Boot, OpenTelemetry, PostgreSQL, Jaeger, and vanilla JavaScript. It collects real OTLP spans from three microservices, stores them for analysis, and presents service health and trace waterfalls in a browser.

## What it includes

- OTLP HTTP Protobuf ingestion at `POST /v1/traces`, including gzip payloads
- Idempotent Span storage with trace, parent, timing, status, HTTP, and attribute data
- Trace summary records and distinct-trace analytics
- Dashboard cards for volume, errors, latency, and slow traces
- Per-service request count, error rate, average, P95, and P99 latency
- Trace explorer with service, status, duration, text, and time-window filters
- Trace detail drawer with a multi-service span waterfall and attributes
- One-click success, slow, and failure scenarios from the Dashboard
- AI-assisted root-cause analysis with severity, evidence, and recommended actions
- CSV export for filtered trace results and JSON download for trace details
- Configurable automatic telemetry retention (30 days by default)
- Automatic filtering and cleanup of platform health-check telemetry
- Normal, slow, and failed distributed trace scenarios
- Jaeger export for an independent trace timeline
- Local Docker Compose stack and a Render cloud Blueprint
- Automated backend and microservice tests

## Architecture

```text
Browser Dashboard :8080
        │
        ▼
TracePilot Backend ─────────────────────────► PostgreSQL
        ▲                  OTLP spans              │
        │                                          │ analytics
OpenTelemetry Collector ───────────────► Jaeger    │
        ▲                                  :16686  │
        │ OTLP HTTP                                │
        │                                          │
order-service :8081 ──► payment-service :8082      │
        └─────────────► inventory-service :8083    │
```

The OpenTelemetry Collector exports every trace to both Jaeger and TracePilot. TracePilot stores all spans in `span_records` and projects server spans into `trace_records` for analytics.

## Quick start with Docker

Requirements: Docker Desktop and Docker Compose.

```bash
cp .env.example .env
docker compose up --build -d
```

Open:

- TracePilot Dashboard: <http://localhost:8080>
- Jaeger: <http://localhost:16686>

Generate real traces:

```bash
curl http://localhost:8081/order/success
curl http://localhost:8081/order/slow
curl -i http://localhost:8081/order/fail
```

The failed scenario intentionally returns HTTP 500. Wait a few seconds for the Collector batch to flush, then refresh the Dashboard.

You can also run all three scenarios from the **Generate distributed traces** section in the Dashboard. The failure button treats the intentional HTTP 500 as a successful demonstration.

Stop the stack:

```bash
docker compose down
```

To also delete local database data:

```bash
docker compose down --volumes
```

## Dashboard

The Dashboard is served by the Spring Boot backend at `/`, so it does not require a separate frontend build. It supports:

- selectable one-hour, 24-hour, seven-day, 30-day, and all-time windows;
- service health and tail-latency comparison;
- combined filters for trace ID, operation, service, status, and duration;
- paginated trace results;
- detailed span waterfall, service list, status, timing, and OTLP attributes;
- direct links from a trace to the matching Jaeger timeline;
- one-click live scenarios without using a terminal;
- AI analysis of the latest trace or any selected trace;
- CSV export, trace ID copy, and complete trace JSON download;
- automatic refresh every 30 seconds and backend health status.

## API

### Trace ingestion and exploration

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/v1/traces` | Receive OTLP HTTP Protobuf traces |
| `GET` | `/api/traces` | Search and paginate server spans |
| `GET` | `/api/traces/services` | List observed services |
| `GET` | `/api/traces/trace/{traceId}` | Return an aggregated trace and ordered spans |
| `GET` | `/api/spans?traceId=...` | Return raw ordered spans |
| `POST` | `/api/traces` | Create a manual trace record for API testing |
| `GET` | `/api/traces/{id}` | Read a manual/server-span record |
| `DELETE` | `/api/traces/{id}` | Delete a record |
| `POST` | `/api/demo/{scenario}` | Run `success`, `slow`, or `fail` through the deployed services |
| `GET` | `/api/ai/status` | Report whether model-backed analysis is configured |
| `POST` | `/api/ai/analyze/{traceId}` | Analyze a trace and return severity, root cause, evidence, and actions |

`GET /api/traces` accepts these optional parameters:

```text
page, size, q, serviceName, status, minDurationMs, hours
```

### Analytics

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/analytics/summary` | Distinct trace volume, failures, slow traces, error rate, and average latency |
| `GET` | `/api/analytics/services` | Service request volume, failures, average, P95, and P99 |
| `GET` | `/api/analytics/slow-traces` | Records above a duration threshold |
| `GET` | `/api/analytics/failed-traces` | Failed records |
| `GET` | `/actuator/health` | Deployment health check |

Summary and service analytics accept an optional `hours` time window. Summary also accepts `slowThresholdMs`.

## Demo service endpoints

| Endpoint | Result |
|---|---|
| `GET /order/success` | Inventory and payment complete normally |
| `GET /order/slow` | Payment waits approximately two seconds |
| `GET /order/fail` | Payment throws an intentional failure |
| `GET /payment/slow?delayMs=2000` | Configurable delay, capped at five seconds |
| `GET /inventory/test` | Inventory availability response |

## Cloud deployment

The repository includes `render.yaml`, which defines the Dashboard/API, PostgreSQL, Jaeger, the Collector, and all three demo services. In Render, create a new Blueprint from this GitHub repository and review the generated resources before applying it. The free-plan configuration uses managed HTTPS URLs between services because free Render web services cannot receive private-network traffic.

Detailed instructions: [Cloud deployment](docs/CLOUD_DEPLOYMENT.md)

### AI analysis

Set `OPENAI_API_KEY` to enable model-backed analysis through the OpenAI Responses API. The default model is `gpt-6-luna`; override it with `OPENAI_MODEL`. The API key is read only from the environment and must not be committed.

When no API key is configured, the hourly model-call limit is reached, or the provider is temporarily unavailable, TracePilot returns a clearly labelled built-in diagnosis based on failed spans, HTTP status, span hierarchy, and latency. The response field `analysisMode` is `OPENAI` only when a model produced the result. Model responses are cached per trace for 10 minutes, model calls default to 30 per hour, and API responses are requested with storage disabled.

The included plans target a portfolio demo. Free instances can sleep and use temporary compute. Use retained PostgreSQL and always-on service plans for production data.

## Manual local development

Requirements: Java 21, Docker Desktop, and `curl`.

1. Create `.env` and start PostgreSQL:

   ```bash
   cp .env.example .env
   docker compose --env-file .env -f tracepilot-backend/docker-compose.yml up -d
   ```

2. Download the Java agent:

   ```bash
   ./scripts/download-otel-agent.sh
   ```

3. Start the backend:

   ```bash
   set -a
   source .env
   set +a
   cd tracepilot-backend
   ./mvnw spring-boot:run
   ```

4. Start Jaeger and the Collector:

   ```bash
   docker compose -f otel-collector/docker-compose.yml up -d
   ```

5. Package and run each microservice with the Java agent. See the service ports and environment variables in the root `docker-compose.yml` for the canonical configuration.

## Tests

Run every module:

```bash
(cd tracepilot-backend && ./mvnw test)
(cd order_service && ./mvnw test)
(cd payment_service && ./mvnw test)
(cd inventory-service && ./mvnw test)
```

Backend tests cover application startup, OTLP and gzip ingestion, server-span projection, trace aggregation, attribute parsing, distinct-trace analytics, failure rates, and tail latency.

Run a cloud acceptance check after deployment:

```bash
./scripts/smoke-test-cloud.sh
```

Override `TRACEPILOT_BASE_URL` or the individual service URL variables to test a different deployment.

## Data retention

TracePilot removes spans and analytics records older than 30 days every day at 03:15 UTC. Set `TRACE_RETENTION_DAYS` to a positive number to change the retention window. This keeps the demo database bounded during long-running cloud use.

Render and Docker health checks are excluded from ingestion, and any older health-check records are removed automatically. Dashboard metrics therefore describe business requests rather than infrastructure polling.

## Repository layout

| Directory | Purpose |
|---|---|
| `tracepilot-backend` | Dashboard, OTLP ingestion, persistence, trace queries, and analytics |
| `order_service` | Entry service and success, slow, and failed trace scenarios |
| `payment_service` | Normal, configurable slow, and failed payment endpoints |
| `inventory-service` | Inventory endpoint used in the distributed call chain |
| `otel-collector` | Collector routing to TracePilot and Jaeger |
| `docs` | Cloud deployment guidance |
| `scripts` | Local setup helpers |

## Security and data notes

- `.env`, build output, logs, and downloaded Java agents are ignored by Git.
- Database passwords come from environment variables and are never committed.
- The demo exposes ingestion and analytics APIs without authentication. Add authentication, OTLP network restrictions, sampling, retention, and backups before using it for production telemetry.
