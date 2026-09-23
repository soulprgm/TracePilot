#!/bin/sh
set -eu

COLLECTOR_OTLP_ENABLED=true \
QUERY_HTTP_SERVER_HOST_PORT=:16686 \
/usr/local/bin/jaeger-all-in-one &

exec /docker-entrypoint.sh nginx -g 'daemon off;'
