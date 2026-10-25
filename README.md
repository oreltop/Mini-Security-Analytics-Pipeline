# Mini WSA — Mini Security Analytics Pipeline

Backend take-home: ingest security DLRs, enrich them, store them, and expose
analytics over REST. Java 21 + Spring Boot 3.3.

Detailed write-ups live under [`docs/`](docs/):

| Doc | What it covers |
|---|---|
| [system paths](docs/system-paths.md) | Happy-path and error-path overview |
| [ingestion](docs/ingestion.md) | Validate → enrich → store; [batch semantics](docs/ingestion.md#batch-semantics) |
| [enrichment](docs/enrichment.md) | Attack type, threat score, `receivedAt` |
| [storage](docs/storage.md) | `EventRepository`, profiles, ClickHouse choice |
| [stats](docs/stats.md) / [samples](docs/samples.md) | Analytics APIs |
| [generator](docs/generator.md) / [rate-limiting](docs/rate-limiting.md) | Tooling & bonus |
| [gaps](docs/gaps.md) | Deferred hardening and unfinished polish |
| [weaknesses](docs/weaknesses.md) | Structural design debts (e.g. identity semantics) |

---

## Prerequisites

- **JDK 21** and the included Maven wrapper (`./mvnw`)
- **Docker** + Compose — only for the ClickHouse full stack (and for
  `-Pintegration` Testcontainers)

---

## Run tests

```bash
# Fast unit / API tests (in_memory profile; excludes Testcontainers)
./mvnw test

# Integration tests against a real ClickHouse (Docker required)
./mvnw test -Pintegration

# Generator smoke checks (bash + jq; needs network only for the --post mock)
./tools/test-generate-events.sh
```

---

## Build & run (local, in-memory)

No database required — default storage profile is `in_memory`
(`spring.profiles.default` in `application.properties`).

```bash
./mvnw spring-boot:run
# equivalent: ./mvnw spring-boot:run -Dspring-boot.run.profiles=in_memory
```

App: [http://localhost:8080](http://localhost:8080)

---

## Build & run (Docker / ClickHouse)

Builds the app image and starts ClickHouse + the app with
`SPRING_PROFILES_ACTIVE=clickhouse`:

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| App | [http://localhost:8080](http://localhost:8080) |
| ClickHouse HTTP | [http://localhost:8123](http://localhost:8123) |

Stop / clean:

```bash
docker compose down
```

Activate **exactly one** of `in_memory` | `clickhouse` — both at once registers two
`EventRepository` beans. Profile rationale: [docs/storage.md](docs/storage.md#profile-choice-design-decision).

---

## Swagger UI & OpenAPI schema

With the app running (local or Compose):

| What | URL |
|---|---|
| **Swagger UI** | [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) |
| **OpenAPI JSON** | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) |
| **OpenAPI YAML** | [http://localhost:8080/v3/api-docs.yaml](http://localhost:8080/v3/api-docs.yaml) |

```bash
# Fetch the schema
curl -sS http://localhost:8080/v3/api-docs | jq .
curl -sS http://localhost:8080/v3/api-docs.yaml
```

---

## Load sample data (optional)

Generator is tooling only (`tools/`), not part of the app jar:

```bash
./tools/generate-events.sh --count 10000 --seed 42 --out /tmp/events.json
curl -sS -X POST http://localhost:8080/v1/events/ingest \
  -H 'Content-Type: application/json' --data-binary @/tmp/events.json
```

**Milestones:** `v0.1-ingestion` · `v0.2-enrichment` · `v0.3-stats` · `v0.4-samples`
· `v0.5-generator` · `v0.6-rate-limiting`

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
              ▼                        ▼                        │
     InMemoryEventRepository   ClickHouseEventRepository        │
     profile: in_memory          profile: clickhouse            │
     ConcurrentHashMap           MergeTree / JDBC               │
              │                        │                        │
              └────────────┬───────────┘                        │
                           │                                    │
         ┌─────────────────┼─────────────────┐                  │
         ▼                 ▼                 ▼                  │
  GET /v1/stats/summary   GET /v1/events/samples   (rate-limited per IP)
  aggregations in store   filter + page in store
```

**Pipeline:** transport binding → semantic validation → classification + threat
score + `receivedAt` → store via `EventRepository` → analytics on the same seam.

**Known contract difference (documented debt):** `in_memory` **upserts** by `eventId`;
`clickhouse` **appends** (duplicate ids possible). See
[docs/weaknesses.md](docs/weaknesses.md).

**Design pillars**

- **Zero-trust ingest:** required fields, enums, unknown JSON rejected; server
  stamps `receivedAt`.
- **Batch validation all-or-nothing:** one invalid event → `400`, nothing from that
  request stored; saves after a successful validate pass are sequential (not one
  DB transaction) — [batch semantics](docs/ingestion.md#batch-semantics).
- **Named storage profiles:** `in_memory` (default for local/tests) or `clickhouse`
  (Compose / prod-like); activate exactly one.
- **Swappable storage:** application code depends on `EventRepository` only.
- **Aggregations in the store:** ClickHouse `GROUP BY` / in-memory streams — not
  “pull all rows and fold in Java.”
- **Java 21 virtual threads** for request handling
  (`spring.threads.virtual.enabled=true`).

---

## API (curl)

Assume the app is on `:8080`. Bodies are enriched events (original fields +
`attackType`, `threatScore`, `receivedAt`).

### Ingest — `POST /v1/events/ingest`

Single object or array. `201` with enriched event(s); `400` with `ErrorResponse`.
**Batch validation is all-or-nothing** — one invalid event rejects the whole request and
nothing from that request is stored ([docs/ingestion.md](docs/ingestion.md#batch-semantics)).

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

Stats and samples paths: **100 requests / minute / client IP**
(`app.rate-limit.requests-per-minute`). Over limit → `429` + `Retry-After`.
Ingest is not limited. See [docs/rate-limiting.md](docs/rate-limiting.md).

---

## Storage justification

Workload: append-heavy security events, read mainly as **time-range aggregations**
(stats) and **filtered scans** (samples) — OLAP / event-analytics, not OLTP.

| Option | Fit |
|---|---|
| **ClickHouse** (`clickhouse` profile) | Strong write throughput, columnar `GROUP BY`, time-order scans, TTL story |
| Elasticsearch | Great search; weaker schema story and heavier ops for our fixed aggregations |
| Postgres | Simple ops; aggregation + scale weaker for this access pattern |

| Profile | When |
|---|---|
| `in_memory` | Default (`spring.profiles.default`) — local run + fast tests, no DB |
| `clickhouse` | Compose sets it; use for production-like / integration tests |

Same `EventRepository` API on both; stats/samples aggregations live in each backend.
Identity on replay still differs (upsert vs append) — tracked in
[docs/weaknesses.md](docs/weaknesses.md). Deeper write-up:
[docs/storage.md](docs/storage.md).

---

## What I’d improve with more time

**Hardening** ([docs/gaps.md](docs/gaps.md)):

- Cap batch size; reject empty batches; align batch error indexing
- Stricter validation (HTTP status range, IP format, timestamp sanity)
- Unified `ErrorResponse` for more Spring default statuses (`405`/`415`, missing stats params)
- ClickHouse batched inserts and table TTL
- Distributed rate-limit store + trusted forwarded IP headers
- Observability (metrics / structured logs) and richer OpenAPI examples
- Optional bonuses still open: alerting rules, time-series buckets

**Design debts** ([docs/weaknesses.md](docs/weaknesses.md)):

- One identity contract across profiles (`eventId` upsert vs append)
- Atomic enrich+persist for repeat-offender scoring
- Persistence all-or-nothing (or an explicit partial-success API) after validate passes

---

## Challenges & how we approached them

1. **Storage without locking the product to one engine** — `EventRepository` early;
   `in_memory` unblocked TDD; ClickHouse behind a named profile with Testcontainers.
2. **Named profiles instead of `!clickhouse`** — both backends are first-class;
   default stays `in_memory` for clone-and-run; Compose / deploy sets `clickhouse`
   explicitly ([profile choice](docs/storage.md#profile-choice-design-decision)).
3. **Making ClickHouse “pay for itself”** — rejected aggregating in Java over a
   full row fetch; pushed `GROUP BY` / top-N into both repository implementations.
4. **Batch meaning** — validation all-or-nothing by design; documented that saves
   after validate are sequential (not one transaction).
5. **Repeat-offender scoring vs zero-trust** — count window uses server
   `receivedAt`, not client `timestamp`.
6. **Test pollution under `@SpringBootTest`** — shared in-memory store +
   repeat-offender IP bonuses; isolated tests with unique `configId` / `clientIp`.
7. **Bash generator determinism** — `RANDOM` updates inside `$(…)` subshells were
   discarded; pick helpers now run in-process under `--seed`.
8. **Product vs tooling** — generator lives under `tools/`, not `src/main`, so it
   never ships in the app artifact.

---

## License / submission

Public GitHub repository for the Akamai CSI take-home assignment.
