#!/usr/bin/env bash
# Simulates an outage of the existing APIs: pauses simulado while requests keep arriving, then resumes it.
#
# Every second it asks for 10 products never requested before, which miss the cache, and for product 1, which is
# cached. It prints, per second, the status codes of each kind of request and the slowest answer.
#
# Needs the test infrastructure (docker compose up -d simulado influxdb grafana) and the application on port 5000.
# Usage: scripts/outage-test.sh
set -euo pipefail
cd "$(dirname "$0")/.."

app=http://localhost:5000
pause_at=5
resume_at=25
seconds=40
results=$(mktemp -d)
trap 'docker compose unpause simulado > /dev/null 2>&1 || true; rm -rf "$results"' EXIT

# One file per request: concurrent appends to a single file can lose lines on some platforms.
request() {
    curl -s -o /dev/null -w "$1 $2 %{http_code} %{time_total}\n" "$app/product/$3/similar" > "$results/$1-$2-$3"
}

curl -s -o /dev/null "$app/product/1/similar"

for second in $(seq 0 $((seconds - 1))); do
    if [[ $second -eq $pause_at ]]; then docker compose pause simulado > /dev/null 2>&1; fi
    if [[ $second -eq $resume_at ]]; then docker compose unpause simulado > /dev/null 2>&1; fi
    for i in $(seq 1 10); do request "$second" new "outage-$second-$i-$RANDOM" & done
    request "$second" cached 1 &
    sleep 1
done
wait

echo "| Second | simulado | New products | Slowest | Cached product | Slowest |"
echo "|---:|---|---|---:|---|---:|"
awk -v pause="$pause_at" -v resume="$resume_at" -v seconds="$seconds" '
    { count[$1, $2, $3]++; codes[$3]; if ($4 > slowest[$1, $2]) slowest[$1, $2] = $4 }
    function summary(second, kind,    code, text) {
        for (code in codes) if (count[second, kind, code]) text = text (text ? ", " : "") count[second, kind, code] " × " code
        return text
    }
    END {
        for (s = 0; s < seconds; s++)
            printf "| %d | %s | %s | %.2f s | %s | %.2f s |\n", s, (s >= pause && s < resume) ? "paused" : "up",
                summary(s, "new"), slowest[s, "new"], summary(s, "cached"), slowest[s, "cached"]
    }' <(cat "$results"/*)
