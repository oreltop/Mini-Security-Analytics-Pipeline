#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GEN="$ROOT/tools/generate-events.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }

# --- 5.1 one valid event ---
"$GEN" --count 1 --out "$TMP/one.json" || fail "generator exited non-zero"
jq -e 'type == "array" and length == 1' "$TMP/one.json" >/dev/null \
  || fail "expected JSON array of length 1"
jq -e '.[0] | .eventId and .timestamp and .configId and .clientIp and .hostname
  and .path and .method and .statusCode and .rule.id and .rule.name
  and .rule.severity and .rule.category and .action
  and (.requestSize != null) and (.responseSize != null)' "$TMP/one.json" >/dev/null \
  || fail "missing required fields on event"
jq -e '.[0].rule.category | IN("INJECTION","XSS","PROTOCOL_VIOLATION","DATA_LEAKAGE","BOT","DOS","RATE_LIMIT")' \
  "$TMP/one.json" >/dev/null || fail "invalid category"
jq -e '.[0].action | IN("DENY","ALERT","MONITOR")' "$TMP/one.json" >/dev/null \
  || fail "invalid action"
echo "OK: 5.1 one valid event"

# --- 5.2 count, seed, out ---
"$GEN" --count 50 --seed 42 --out "$TMP/a.json"
"$GEN" --count 50 --seed 42 --out "$TMP/b.json"
jq -e 'type == "array" and length == 50' "$TMP/a.json" >/dev/null || fail "count 50"
cmp -s "$TMP/a.json" "$TMP/b.json" || fail "same seed should be deterministic"
"$GEN" --count 50 --seed 99 --out "$TMP/c.json"
cmp -s "$TMP/a.json" "$TMP/c.json" && fail "different seeds should differ" || true
echo "OK: 5.2 count/seed/out"

# --- 5.3 attack waves: some clientIp+path appears >= WAVE_SIZE (25) ---
"$GEN" --count 200 --seed 7 --out "$TMP/waves.json"
max_burst="$(jq -r '
  group_by(.clientIp + "\t" + .path)
  | map(length)
  | max
' "$TMP/waves.json")"
[[ "$max_burst" -ge 25 ]] || fail "expected a wave burst >= 25, got $max_burst"
echo "OK: 5.3 attack waves (max burst=$max_burst)"

# --- 5.4 optional --post (mock HTTP ingest; Python only for the mock, not the generator) ---
PORT=18080
python3 - "$PORT" "$TMP/posted.json" <<'PY' &
import json, sys
from http.server import BaseHTTPRequestHandler, HTTPServer

port = int(sys.argv[1])
out = sys.argv[2]

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        n = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(n)
        with open(out, "wb") as f:
            f.write(body)
        self.send_response(201)
        self.end_headers()
        self.wfile.write(b"[]")
    def log_message(self, *args):
        pass

HTTPServer(("127.0.0.1", port), Handler).handle_request()
PY
MOCK_PID=$!
sleep 0.3
"$GEN" --count 3 --seed 1 --out "$TMP/post-body.json" --post --url "http://127.0.0.1:${PORT}/v1/events/ingest" \
  || fail "--post failed"
wait "$MOCK_PID" || true
jq -e 'type == "array" and length == 3' "$TMP/posted.json" >/dev/null \
  || fail "mock did not receive a 3-event array"
echo "OK: 5.4 --post"

echo "ALL GENERATOR CHECKS PASSED"
