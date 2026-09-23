#!/usr/bin/env bash
set -euo pipefail

tracepilot_url="${TRACEPILOT_BASE_URL:-https://tracepilot-backend.onrender.com}"
jaeger_url="${JAEGER_URL:-https://tracepilot-jaeger.onrender.com}"
order_url="${ORDER_SERVICE_URL:-https://tracepilot-order.onrender.com}"
payment_url="${PAYMENT_SERVICE_URL:-https://tracepilot-payment.onrender.com}"
inventory_url="${INVENTORY_SERVICE_URL:-https://tracepilot-inventory.onrender.com}"
collector_url="${COLLECTOR_URL:-https://tracepilot-collector.onrender.com}"

check_status() {
  local label="$1"
  local expected="$2"
  local url="$3"
  local actual
  actual="$(curl --silent --show-error --location --max-time 120 --output /dev/null --write-out '%{http_code}' "$url")"
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL  $label returned HTTP $actual (expected $expected)"
    return 1
  fi
  echo "PASS  $label returned HTTP $actual"
}

check_status "TracePilot health" 200 "$tracepilot_url/actuator/health"
check_status "TracePilot dashboard" 200 "$tracepilot_url/"
check_status "TracePilot AI status" 200 "$tracepilot_url/api/ai/status"
check_status "Jaeger" 200 "$jaeger_url/"
check_status "Inventory" 200 "$inventory_url/actuator/health"
check_status "Payment" 200 "$payment_url/actuator/health"
check_status "Order" 200 "$order_url/actuator/health"
check_status "Success scenario" 200 "$order_url/order/success"
check_status "Slow scenario" 200 "$order_url/order/slow"
check_status "Failure scenario" 500 "$order_url/order/fail"

otlp_status="$(curl --silent --show-error --location --max-time 120 \
  --request POST \
  --header 'Content-Type: application/x-protobuf' \
  --data-binary '' \
  --output /dev/null \
  --write-out '%{http_code}' \
  "$collector_url/v1/traces")"
if [[ "$otlp_status" != "200" ]]; then
  echo "FAIL  OTLP ingestion returned HTTP $otlp_status (expected 200)"
  exit 1
fi
echo "PASS  OTLP ingestion returned HTTP 200"

echo "TracePilot cloud acceptance check passed."
