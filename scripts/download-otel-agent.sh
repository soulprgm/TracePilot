#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
agent_version="${OTEL_AGENT_VERSION:-2.31.1}"
download_url="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${agent_version}/opentelemetry-javaagent.jar"
temporary_jar="$(mktemp /tmp/opentelemetry-javaagent.XXXXXX.jar)"

cleanup() {
  rm -f "$temporary_jar"
}
trap cleanup EXIT

echo "Downloading OpenTelemetry Java Agent ${agent_version}"
curl --fail --location --output "$temporary_jar" "$download_url"

for service in order_service payment_service inventory-service; do
  destination="$repository_root/$service/otel/opentelemetry-javaagent.jar"
  mkdir -p "$(dirname "$destination")"
  install -m 0644 "$temporary_jar" "$destination"
  echo "Installed $destination"
done
