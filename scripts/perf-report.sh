#!/usr/bin/env bash
# Runs the k6 test of the statement and prints its results per scenario as a markdown table.
#
# Needs the test infrastructure (docker compose up -d simulado influxdb grafana) and the
# application listening on port 5000. Given a file name, it also saves a screenshot of the
# Grafana dashboard for the run with a Chromium-based browser ($CHROME, chromium by default).
#
# Usage: scripts/perf-report.sh [screenshot.png]
set -euo pipefail
cd "$(dirname "$0")/.."

app=http://localhost:5000
# Scenario and the product it requests, in the order of shared/k6/test.js.
scenarios="normal:1 notFound:4 error:5 slow:2 verySlow:3"
# Every scenario keeps its virtual users busy for 10 seconds.
scenario_seconds=10

# simulado slows down as it serves requests, so every run starts with a fresh one.
docker compose restart simulado > /dev/null 2>&1
for _ in $(seq 30); do curl -sf http://localhost:3001/product/1/similarids > /dev/null && break; sleep 1; done

start=$(date +%s)
docker compose run --rm k6 run scripts/test.js > /dev/null
end=$(date +%s)

influx() {
    docker compose exec -T influxdb influx -database k6 -format csv -execute "$1" | tail -n +2
}
window="time >= ${start}s AND time <= ${end}s"
durations=$(influx "SELECT count(value), percentile(value, 50), percentile(value, 95), percentile(value, 99), max(value) FROM http_req_duration WHERE $window GROUP BY scenario")
errors=$(influx "SELECT count(value) FROM http_reqs WHERE $window AND status != '200' GROUP BY scenario")

products() {
    curl -s "$app/product/$1/similar" | { grep -o '"id":"[^"]*"' || true; } | cut -d'"' -f4 | paste -sd, - | sed 's/,/, /g'
}

echo "| Scenario | Requests | Throughput | p50 | p95 | p99 | Max | Errors | Products returned |"
echo "|---|---:|---:|---:|---:|---:|---:|---:|---|"
for entry in $scenarios; do
    scenario=${entry%%:*}
    IFS=, read -r _ _ _ requests p50 p95 p99 max <<< "$(grep ",scenario=$scenario," <<< "$durations")"
    failed=$(grep ",scenario=$scenario," <<< "$errors" | cut -d, -f4 || true)
    returned=$(products "${entry##*:}")
    awk -v s="$scenario" -v n="$requests" -v f="${failed:-0}" -v t="$scenario_seconds" \
        -v p50="$p50" -v p95="$p95" -v p99="$p99" -v max="$max" -v r="${returned:-none}" \
        'BEGIN { printf "| %s | %d | %.0f req/s | %.0f ms | %.0f ms | %.0f ms | %.0f ms | %.1f %% | %s |\n",
                 s, n, n / t, p50, p95, p99, max, 100 * f / n, r }'
done

if [[ $# -gt 0 ]]; then
    "${CHROME:-chromium}" --headless=new --hide-scrollbars --window-size=1600,940 --virtual-time-budget=20000 \
        --screenshot="$1" \
        "http://localhost:3000/d/Le2Ku9NMk/k6-performance-test?orgId=1&from=$((start * 1000))&to=$((end * 1000))&kiosk" \
        2> /dev/null
fi
