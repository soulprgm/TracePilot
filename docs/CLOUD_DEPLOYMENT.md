# TracePilot cloud deployment

TracePilot includes a Render Blueprint at `render.yaml`. The Blueprint creates the Dashboard and API, PostgreSQL, the three demo services, the OpenTelemetry Collector, and Jaeger from the same GitHub repository.

## Deploy on Render

1. Push the repository to GitHub.
2. In Render, choose **New > Blueprint**.
3. Connect `soulprgm/TracePilot` and select `render.yaml`.
4. Review the six web services and PostgreSQL database before applying the Blueprint.
5. After deployment, open the `tracepilot-backend` URL for the Dashboard.

The Blueprint uses free plans for a reviewable demo. Free services can sleep when idle and may take time to wake. Increase the plan for always-on use or higher trace volume.

## Generate cloud traces

Open these endpoints on the deployed `tracepilot-order` service:

- `/order/success` creates a normal distributed trace.
- `/order/slow` creates a trace with an approximately two-second payment span.
- `/order/fail` creates an HTTP 500 trace.

The Collector exports each trace to both TracePilot and Jaeger. The Dashboard refreshes automatically every 30 seconds.

## Configuration

Render injects database credentials and private service addresses. Secrets are not stored in Git. If Render assigns a suffix to the Jaeger service URL, update the backend `JAEGER_URL` environment variable so the Dashboard's **Open in Jaeger** link uses the final public URL.

## Production notes

The Blueprint is designed for demonstrations and portfolio use. A production deployment should use paid always-on instances, a retained PostgreSQL plan, authentication for the Dashboard and APIs, HTTPS-only ingestion, trace sampling, data retention, backups, and network restrictions around OTLP ingestion.
