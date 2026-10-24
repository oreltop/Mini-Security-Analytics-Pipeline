#!/usr/bin/env bash
# Generate realistic-looking security event (DLR) JSON for Mini WSA.
# Tooling only — not part of the application runtime.
set -euo pipefail

COUNT=1
SEED=""
OUT=""
POST=0
URL="http://localhost:8080/v1/events/ingest"
WAVE_SIZE=25
WAVE_EVERY=100

usage() {
  cat <<'USAGE'
Usage: generate-events.sh [--count N] [--seed N] [--out FILE] [--post] [--url URL]

  --count N   Number of events (default: 1; use 10000 for a full load)
  --seed N    Seed bash RANDOM for reproducibility
  --out FILE  Write JSON array to FILE (default: stdout)
  --post      POST the array to the ingest API (also writes --out if set)
  --url URL   Ingest URL when --post (default: http://localhost:8080/v1/events/ingest)
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --count) COUNT="$2"; shift 2 ;;
    --seed) SEED="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --post) POST=1; shift ;;
    --url) URL="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
  esac
done

if [[ -n "$SEED" ]]; then
  RANDOM="$SEED"
fi

CATEGORIES=(INJECTION XSS PROTOCOL_VIOLATION DATA_LEAKAGE BOT DOS RATE_LIMIT)
ACTIONS=(DENY ALERT MONITOR)
SEVERITIES=(CRITICAL HIGH MEDIUM LOW)
PATHS=("/api/v1/login" "/admin" "/" "/wp-login.php" "/api/v1/users" "/search" "/static/app.js")
METHODS=(GET POST PUT DELETE)
HOSTS=("www.example.com" "api.example.com" "shop.example.com")
COUNTRIES=("CN" "US" "RU" "BR" "DE" "IN")
CITIES=("Beijing" "New York" "Moscow" "Sao Paulo" "Berlin" "Mumbai")
USER_AGENTS=(
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15"
  "python-requests/2.31.0"
  "curl/8.5.0"
)

rule_for_category() {
  case "$1" in
    INJECTION) echo "950001|SQL_INJECTION|SQL Injection Attack Detected" ;;
    XSS) echo "950002|XSS_FILTER|Cross-Site Scripting Detected" ;;
    PROTOCOL_VIOLATION) echo "950003|PROTOCOL_ANOMALY|Malformed HTTP Request" ;;
    DATA_LEAKAGE) echo "950004|DATA_EXFIL|Sensitive Data Exposure" ;;
    BOT) echo "950005|BOT_DETECTION|Automated Client Detected" ;;
    DOS) echo "950006|DOS_PATTERN|Denial of Service Pattern" ;;
    RATE_LIMIT) echo "950007|RATE_LIMIT|Rate Limit Triggered" ;;
  esac
}

# Pick/random helpers must run in the current shell (not $(...)) so RANDOM advances
# under --seed in a reproducible way.
pick_into() {
  local -n _arr=$1
  local -n _dest=$2
  _dest="${_arr[RANDOM % ${#_arr[@]}]}"
}

random_ip_into() {
  local -n _dest=$1
  _dest="203.0.$((RANDOM % 256)).$((1 + RANDOM % 254))"
}

ANCHOR_EPOCH=1747746730
iso_timestamp_into() {
  local -n _dest=$1
  local offset=$((RANDOM % 86400))
  _dest="$(date -u -d "@$((ANCHOR_EPOCH + offset))" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null \
    || date -u -r "$((ANCHOR_EPOCH + offset))" +"%Y-%m-%dT%H:%M:%SZ")"
}

status_for_action() {
  case "$1" in
    DENY) echo 403 ;;
    ALERT) echo 200 ;;
    MONITOR) echo 200 ;;
  esac
}

emit_event() {
  local event_id="$1"
  local client_ip="$2"
  local path="$3"
  local category action severity rule_meta rule_id rule_name rule_msg
  local method hostname country city ua status config_id req_sz res_sz ts

  pick_into CATEGORIES category
  pick_into ACTIONS action
  pick_into SEVERITIES severity
  rule_meta="$(rule_for_category "$category")"
  IFS='|' read -r rule_id rule_name rule_msg <<<"$rule_meta"
  pick_into METHODS method
  pick_into HOSTS hostname
  pick_into COUNTRIES country
  pick_into CITIES city
  pick_into USER_AGENTS ua
  status="$(status_for_action "$action")"
  config_id=$((14000 + RANDOM % 50))
  req_sz=$((100 + RANDOM % 5000))
  res_sz=$((50 + RANDOM % 2000))
  iso_timestamp_into ts

  jq -nc \
    --arg eventId "$event_id" \
    --arg timestamp "$ts" \
    --argjson configId "$config_id" \
    --arg policyId "pol_web1" \
    --arg clientIp "$client_ip" \
    --arg hostname "$hostname" \
    --arg path "$path" \
    --arg method "$method" \
    --argjson statusCode "$status" \
    --arg userAgent "$ua" \
    --arg ruleId "$rule_id" \
    --arg ruleName "$rule_name" \
    --arg ruleMessage "$rule_msg" \
    --arg severity "$severity" \
    --arg category "$category" \
    --arg action "$action" \
    --arg country "$country" \
    --arg city "$city" \
    --argjson requestSize "$req_sz" \
    --argjson responseSize "$res_sz" \
    '{
      eventId: $eventId,
      timestamp: $timestamp,
      configId: $configId,
      policyId: $policyId,
      clientIp: $clientIp,
      hostname: $hostname,
      path: $path,
      method: $method,
      statusCode: $statusCode,
      userAgent: $userAgent,
      rule: {
        id: $ruleId,
        name: $ruleName,
        message: $ruleMessage,
        severity: $severity,
        category: $category
      },
      action: $action,
      geoLocation: { country: $country, city: $city },
      requestSize: $requestSize,
      responseSize: $responseSize
    }'
}

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT
NDJSON="$WORKDIR/events.ndjson"
: >"$NDJSON"

wave_ip=""
wave_path=""
wave_left=0

for ((i = 0; i < COUNT; i++)); do
  if ((WAVE_SIZE > 0 && COUNT >= WAVE_SIZE && i % WAVE_EVERY == 0)); then
    random_ip_into wave_ip
    pick_into PATHS wave_path
    wave_left=$WAVE_SIZE
  fi

  if ((wave_left > 0)); then
    ip="$wave_ip"
    path="$wave_path"
    wave_left=$((wave_left - 1))
  else
    random_ip_into ip
    pick_into PATHS path
  fi

  emit_event "evt-$(printf '%06d' "$i")" "$ip" "$path" >>"$NDJSON"
done

events_json="$(jq -s '.' "$NDJSON")"

if [[ -n "$OUT" ]]; then
  printf '%s\n' "$events_json" >"$OUT"
else
  printf '%s\n' "$events_json"
fi

if ((POST)); then
  body_file="$WORKDIR/body.json"
  printf '%s\n' "$events_json" >"$body_file"
  curl -sS -f -X POST "$URL" \
    -H "Content-Type: application/json" \
    --data-binary @"$body_file" >/dev/null
  echo "Posted $COUNT events to $URL" >&2
fi
