# TracePilot cloud deployment

TracePilot includes a Render Blueprint at `render.yaml`. The Blueprint creates the Dashboard and API, PostgreSQL, the three demo services, the OpenTelemetry Collector, and Jaeger from the same GitHub repository.

## Deploy on Render

1. Push the repository to GitHub.
2. In Render, choose **New > Blueprint**.
3. Connect `soulprgm/TracePilot` and select `render.yaml`.
4. Review the six web services and PostgreSQL database before applying the Blueprint.
5. After deployment, open the `tracepilot-backend` URL for the Dashboard.

The Blueprint uses free plans for a reviewable demo. Free services can sleep when idle and may take time to wake. Because free Render web services cannot receive private-network traffic, the demo services communicate through their managed HTTPS URLs. Increase the plan for always-on use, private service-to-service traffic, or higher trace volume.

## Generate cloud traces

Open these endpoints on the deployed `tracepilot-order` service:

- `/order/success` creates a normal distributed trace.
- `/order/slow` creates a trace with an approximately two-second payment span.
- `/order/fail` creates an HTTP 500 trace.

The Collector exports each trace to both TracePilot and Jaeger. The Dashboard refreshes automatically every 30 seconds.

The Dashboard also provides **Run success**, **Run slow**, and **Run failure** buttons. They invoke the same deployed order flow and refresh the trace explorer after ingestion.

## Configuration

Render injects database credentials, while the Blueprint configures the public HTTPS service URLs. Secrets are not stored in Git. The cloud Jaeger container uses an Nginx gateway so its UI and OTLP HTTP receiver can share Render's single public port. If Render assigns a suffix to a service URL, update the corresponding URL values in `render.yaml` before syncing again.

`TRACE_RETENTION_DAYS` defaults to 30. The backend deletes older span and analytics data daily so the demonstration database remains bounded.

The Blueprint declares `OPENAI_API_KEY` as a secret that must be entered in Render. Add an OpenAI API key to enable model-backed root-cause analysis. `OPENAI_MODEL` defaults to `gpt-6-luna`. If the secret is absent, the analysis page remains usable in clearly labelled built-in diagnosis mode. Results are cached per trace for 10 minutes, and `TRACEPILOT_AI_MAX_REQUESTS_PER_HOUR` limits provider calls to 30 per instance by default.

## Acceptance check

After every deployment, run:

```bash
./scripts/smoke-test-cloud.sh
```

The check verifies the Dashboard API, Jaeger, all three microservices, all order scenarios, and OTLP ingestion. The failure scenario must return HTTP 500; the script treats that as expected.

## Production notes

The Blueprint is designed for demonstrations and portfolio use. A production deployment should use paid always-on instances, a retained PostgreSQL plan, authentication for the Dashboard and APIs, HTTPS-only ingestion, trace sampling, data retention, backups, and network restrictions around OTLP ingestion.
