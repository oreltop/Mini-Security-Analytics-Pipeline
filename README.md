# Mini WSA — Mini Security Analytics Pipeline

Backend take-home: ingest security DLRs, enrich them, store them, and expose
analytics over REST. Java 21 + Spring Boot 3.3.

Detailed per-step write-ups live under [`docs/`](docs/)
([ingestion](docs/ingestion.md), [enrichment](docs/enrichment.md),
[storage](docs/storage.md), [stats](docs/stats.md), [samples](docs/samples.md),
[generator](docs/generator.md), [rate-limiting](docs/rate-limiting.md),
[gaps](docs/gaps.md)).

---

## Build & run

**Requirements:** JDK 21, Maven wrapper (included). Docker optional for the
ClickHouse full stack.

```bash
# Unit tests (excludes Testcontainers)
./mvnw test

# Integration tests (ClickHouse via Testcontainers)
./mvnw test -Pintegration

# Zero-infra run (in-memory store)
./mvnw spring-boot:run
# → http://localhost:8080  ·  Swagger UI: /swagger-ui/index.html

# Full stack (app + ClickHouse)
docker compose up --build
```

Generate demo traffic (tooling only — not part of the app jar):

```bash
./tools/generate-events.sh --count 10000 --seed 42 --out /tmp/events.json
curl -sS -X POST http://localhost:8080/v1/events/ingest \
  -H 'Content-Type: application/json' --data-binary @/tmp/events.json
```

**Milestones:** `v0.1-ingestion` · `v0.2-enrichment` · `v0.3-stats` · `v0.4-samples`
· `v0.5-generator`

---

## Architecture

```
                    ┌─────────────────────────────────────────┐
  POST /v1/events/ingest                                      │
                    │  validate → enrich → EventRepository     │
                    └──────────────────┬──────────────────────┘
                                       │
              ┌────────────────────────┼────────────────────────┐
              │                        │                        │
              ▼                        ▼                        ▼
     InMemoryEventRepository   ClickHouseEventRepository   (profile)
     ConcurrentHashMap           MergeTree / JDBC            clickhouse
              │                        │
              └────────────┬───────────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
  GET /v1/stats/summary   GET /v1/events/samples   (rate-limited per IP)
  aggregations in store   filter + page in store
```

**Pipeline:** transport binding → semantic validation → classification + threat
score + `receivedAt` → upsert by `eventId` → analytics queries on the same seam.

**Design pillars**

- **Zero-trust ingest:** required fields, enums, unknown JSON rejected; server
  stamps `receivedAt`.
- **Swappable storage:** application code depends on `EventRepository` only.
- **Aggregations in the store (Y):** ClickHouse `GROUP BY` / in-memory streams —
  not “pull all rows and fold in Java.”
- **Java 21 virtual threads** enabled for request handling
  (`spring.threads.virtual.enabled=true`).

---

## API (curl)

Assume the app is on `:8080`. Bodies are enriched events (original fields +
`attackType`, `threatScore`, `receivedAt`).

### Ingest — `POST /v1/events/ingest`

Single object or array. `201` with enriched event(s); `400` with `ErrorResponse`.

```bash
curl -sS -X POST http://localhost:8080/v1/events/ingest \
  -H 'Content-Type: application/json' \
  -d '{
    "eventId": "evt-00132",
    "timestamp": "2026-05-20T14:32:10Z",
    "configId": 14227,
    "policyId": "pol_web1",
    "clientIp": "203.0.113.42",
    "hostname": "www.example.com",
    "path": "/api/v1/login",
    "method": "POST",
    "statusCode": 403,
    "userAgent": "Mozilla/5.0",
    "rule": {
      "id": "950001", "name": "SQL_INJECTION", "message": "SQLi",
      "severity": "CRITICAL", "category": "INJECTION"
    },
    "action": "DENY",
    "geoLocation": { "country": "CN", "city": "Beijing" },
    "requestSize": 1024,
    "responseSize": 256
  }'
```

### Stats — `GET /v1/stats/summary`

`from` / `to` required (event `timestamp`). `configId` optional.

```bash
curl -sS 'http://localhost:8080/v1/stats/summary?configId=14227&from=2026-05-20T00:00:00Z&to=2026-05-21T00:00:00Z'
```

Returns `totalEvents`, `byCategory`, `byAction`, `topAttackers`, `topTargetedPaths`.

### Samples — `GET /v1/events/samples`

All filters optional. Default `limit=20`, max clamped to `100`. Newest first.

```bash
curl -sS 'http://localhost:8080/v1/events/samples?configId=14227&category=INJECTION&limit=20&offset=0'
```

Envelope: `{ total, limit, offset, items }`.

### Rate limit (bonus)

Stats and samples: **100 requests / minute / client IP**. Over limit → `429` +
`Retry-After`. Ingest is not limited. See [docs/rate-limiting.md](docs/rate-limiting.md).

---

## Storage justification

Workload: append-only security events, read mainly as **time-range aggregations**
(stats) and **filtered scans** (samples) — OLAP / event-analytics, not OLTP.

| Option | Fit |
|---|---|
| **ClickHouse** (chosen for “real” profile) | Strong write throughput, columnar `GROUP BY`, time-order scans, TTL story |
| Elasticsearch | Great search; weaker schema story and heavier ops for our fixed aggregations |
| Postgres | Simple ops; aggregation + scale weaker for this access pattern |

**Default profile is in-memory** so `./mvnw spring-boot:run` needs no database.
Activate ClickHouse with the `clickhouse` profile (`docker compose up --build`).
Same `EventRepository` contract; stats/samples aggregations implemented on both
backends. Deeper scorecard: personal notes / [docs/storage.md](docs/storage.md).

---

## What I’d improve with more time

Highlights from [docs/gaps.md](docs/gaps.md):

- Cap batch size; reject empty batches; align batch error indexing
- Stricter validation (HTTP status range, IP format, timestamp sanity)
- Unified `ErrorResponse` for more Spring default statuses
- ClickHouse batched inserts + table TTL
- Distributed rate-limit store + trusted forwarded IP headers
- Observability (metrics / structured logs) and richer OpenAPI examples
- Optional bonuses still open: alerting rules, time-series buckets

---

## Challenges & how we approached them

1. **Storage without locking the product to one engine** — introduced
   `EventRepository` early; in-memory unblocked TDD; ClickHouse landed behind a
   profile with Testcontainers integration tests.
2. **Making ClickHouse “pay for itself”** — rejected aggregating in Java over a
   full row fetch; pushed `GROUP BY` / top-N into both repository implementations
   so the architecture story stays honest.
3. **Repeat-offender scoring vs zero-trust** — count window uses server
   `receivedAt`, not client `timestamp`.
4. **Test pollution under `@SpringBootTest`** — shared in-memory store +
   repeat-offender IP bonuses; isolated tests with unique `configId` / `clientIp`.
5. **Bash generator determinism** — `RANDOM` updates inside `$(…)` subshells were
   discarded; pick helpers now run in-process under `--seed`.
6. **Product vs tooling** — generator lives under `tools/`, not `src/main`, so it
   never ships in the app artifact.

---

## License / submission

Public GitHub repository for the Akamai CSI take-home assignment.
