#!/usr/bin/env sh

set -eu

APP_URL="${APP_URL:-http://localhost:8080}"
ACTUATOR_URL="${ACTUATOR_URL:-http://localhost:9090}"

request() {
    method="$1"
    name="$2"
    url="$3"
    body="${4:-}"

    printf '\n## %s\n' "$name"
    printf '%s %s\n\n' "$method" "$url"

    if [ -n "$body" ]; then
        curl --fail --silent --show-error \
            --connect-timeout 2 \
            --max-time 10 \
            --request "$method" \
            --header 'Accept: application/json' \
            --header 'Content-Type: application/json' \
            --data "$body" \
            "$url"
    else
        curl --fail --silent --show-error \
            --connect-timeout 2 \
            --max-time 10 \
            --request "$method" \
            --header 'Accept: application/json' \
            "$url"
    fi

    printf '\n'
}

request GET "Actuator endpoints" "$ACTUATOR_URL/actuator"
request GET "Health" "$ACTUATOR_URL/actuator/health"
request GET "Info" "$ACTUATOR_URL/actuator/info"
request GET "Metrics list" "$ACTUATOR_URL/actuator/metrics"

request POST "Partner down" "$APP_URL/partners/system/down"
request GET "Partner health" "$ACTUATOR_URL/actuator/health/partnerSystem" || true
request POST "Partner up" "$APP_URL/partners/system/up"

request POST "Send notification" "$APP_URL/notifications/send?channel=email"
request GET "Notification counter" "$ACTUATOR_URL/actuator/metrics/app.notification.sent"

request POST "Enqueue pending notification" "$APP_URL/notifications/pending?message=retry"
request GET "Pending notification gauge" "$ACTUATOR_URL/actuator/metrics/app.notification.pending.size"
request DELETE "Dequeue pending notification" "$APP_URL/notifications/pending"

request POST "Convert file" "$APP_URL/files/convert?fileName=monthly-report.csv&format=pdf"
request GET "File conversion timer" "$ACTUATOR_URL/actuator/metrics/app.file.conversion.time"

request POST "Validate document fail" "$APP_URL/documents/validate?valid=false"
request GET "Document validation fail timer" "$ACTUATOR_URL/actuator/metrics/app.document.validation.time?tag=result:fail"

request POST "Generate report" "$APP_URL/reports/generate?reportType=weekly-sales"
request GET "Report generation timer" "$ACTUATOR_URL/actuator/metrics/app.report.generation.time"

request GET "Search content" "$APP_URL/contents/search?keyword=observability"
request GET "Content search percentile p95" "$ACTUATOR_URL/actuator/metrics/app.content.search.time.percentile?tag=phi:0.95"

request GET "HTTP metrics" "$ACTUATOR_URL/actuator/metrics/http.server.requests"

request POST "Set feature" "$ACTUATOR_URL/actuator/features/dark-mode" '{"enabled": true}'
request GET "Feature toggles" "$ACTUATOR_URL/actuator/features"
request DELETE "Remove feature" "$ACTUATOR_URL/actuator/features/dark-mode"
