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
