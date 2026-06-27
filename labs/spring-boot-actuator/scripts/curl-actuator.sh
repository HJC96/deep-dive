#!/usr/bin/env sh

set -eu

BASE_URL="${BASE_URL:-http://localhost:18080}"

request() {
    name="$1"
    path="$2"

    printf '\n## %s\n' "$name"
    printf 'GET %s%s\n\n' "$BASE_URL" "$path"

    curl --fail --silent --show-error \
        --connect-timeout 2 \
        --max-time 5 \
        --header 'Accept: application/json' \
        "$BASE_URL$path"

    printf '\n'
}

request "Exposed actuator endpoints" "/actuator"
request "Health endpoint" "/actuator/health"
request "Custom health indicator component" "/actuator/health/learning"
request "Application info" "/actuator/info"
request "Metrics endpoint list" "/actuator/metrics"
request "JVM memory metric sample" "/actuator/metrics/jvm.memory.used"
request "HTTP server request metric sample" "/actuator/metrics/http.server.requests"
